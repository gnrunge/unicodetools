package org.unicode.props;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.util.RegexUtilities;
import org.unicode.draft.CldrUtility.VariableReplacer;
import org.unicode.draft.UnicodeDataInput;
import org.unicode.draft.UnicodeDataInput.ItemReader;
import org.unicode.draft.UnicodeDataOutput;
import org.unicode.draft.UnicodeDataOutput.ItemWriter;
import org.unicode.idna.Regexes;
import org.unicode.props.PropertyUtilities.Joiner;
import org.unicode.props.PropertyUtilities.Merge;
import org.unicode.text.UCD.Default;
import org.unicode.text.utility.Utility;

import sun.text.normalizer.UTF16;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.dev.util.UnicodeProperty;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.Transform;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UnicodeSet;

/**
 * TODO StandardizedVariants and NameSequences*
 * @author markdavis
 *
 */
public class IndexUnicodeProperties extends UnicodeProperty.Factory {
	/**
	 * Control file caching
	 */
	static final boolean GZIP = true;
	static final boolean SIMPLE_COMPRESSION = true;
	static final boolean FILE_CACHE = true;

	/**
	 * Debugging
	 */
	private static final boolean SHOW_DEFAULTS = false;
	private static final boolean CHECK_PROPERTY_STATUS = false;
	private static final UcdProperty CHECK_MISSING = null; // UcdProperty.Numeric_Value; //

	/**
	 * General constants
	 */
	public final static Pattern SEMICOLON = Pattern.compile("\\s*;\\s*");
	public final static Pattern EQUALS = Pattern.compile("\\s*=\\s*");
	public final static Pattern SPACE = Pattern.compile("\\s+");
	static Pattern SLASHX = Pattern.compile("\\\\x\\{([0-9A-Fa-f]{1,6})\\}");
	static Normalizer2 NFD = Normalizer2.getNFDInstance(); //
	//static Normalizer2 NFD2 = Normalizer2.getInstance(null, "NFC", Mode.DECOMPOSE);

	static final String CONSTRUCTED_NAME = "$HANGUL_SYLLABLE$";

	static final Pattern SIMPLE_MISSING_PATTERN = Pattern.compile("\\s*#\\s*@(missing|empty)");

	static final Pattern MISSING_PATTERN = Pattern.compile(
			"\\s*#\\s*@(missing|empty):?" +
					"\\s*([A-Fa-f0-9]+)..([A-Fa-f0-9]+)\\s*;" +
					"\\s*([^;]*)" +
					"(?:\\s*;\\s*([^;]*)" +
					"(?:\\s*;\\s*([^;]*))?)?\\s*" +
					";?"
			);

	public final static String FIELD_SEPARATOR = "; ";
	public final static Pattern TAB = Pattern.compile("[ ]*\t[ ]*");
	private static final boolean SHOW_LOADED = false;
	static final Relation<UcdProperty,String> DATA_LOADING_ERRORS
	= Relation.of(new EnumMap<UcdProperty,Set<String>>(UcdProperty.class), LinkedHashSet.class);

	public static final EnumSet<UcdProperty> DEPRECATED_PROPERTY = EnumSet.of(
			UcdProperty.Grapheme_Link,
			UcdProperty.Hyphen,
			UcdProperty.ISO_Comment,
			UcdProperty.Expands_On_NFC,
			UcdProperty.Expands_On_NFD,
			UcdProperty.Expands_On_NFKC,
			UcdProperty.Expands_On_NFKD,
			UcdProperty.FC_NFKC_Closure);

	public static final EnumSet<UcdProperty> STABLIZED_PROPERTY = EnumSet.of(
			UcdProperty.Hyphen,

			UcdProperty.ISO_Comment);

	public static final EnumSet<UcdProperty> OBSOLETE_PROPERTY = EnumSet.of(
			UcdProperty.ISO_Comment);

	public static final EnumSet<UcdProperty> CONTRIBUTORY_PROPERTY = EnumSet.of(
			UcdProperty.Jamo_Short_Name,
			UcdProperty.Other_Alphabetic,
			UcdProperty.Other_Default_Ignorable_Code_Point,
			UcdProperty.Other_Grapheme_Extend,
			UcdProperty.Other_ID_Continue,
			UcdProperty.Other_ID_Start,
			UcdProperty.Other_Lowercase,
			UcdProperty.Other_Math,
			UcdProperty.Other_Uppercase
			);

	public static final EnumSet<UcdProperty> INFORMATIVE_PROPERTY = EnumSet.of(
			UcdProperty.Alphabetic,
			UcdProperty.Bidi_Mirroring_Glyph,
			UcdProperty.Case_Ignorable,
			UcdProperty.Cased,
			UcdProperty.Changes_When_Casefolded,
			UcdProperty.Changes_When_Casemapped,
			UcdProperty.Changes_When_Lowercased,
			UcdProperty.Changes_When_NFKC_Casefolded,
			UcdProperty.Changes_When_Titlecased,
			UcdProperty.Changes_When_Uppercased,
			UcdProperty.Dash,
			UcdProperty.Diacritic,
			UcdProperty.East_Asian_Width,
			UcdProperty.Extender,
			UcdProperty.Grapheme_Cluster_Break,
			UcdProperty.Grapheme_Link,
			UcdProperty.Hex_Digit,
			UcdProperty.Hyphen,
			UcdProperty.ID_Continue,
			UcdProperty.ID_Start,
			UcdProperty.Ideographic,
			UcdProperty.ISO_Comment,
			UcdProperty.Lowercase,
			UcdProperty.Lowercase_Mapping,
			UcdProperty.Math,
			UcdProperty.NFKC_Casefold,
			UcdProperty.Quotation_Mark,
			UcdProperty.Script,
			UcdProperty.Script_Extensions,
			UcdProperty.Sentence_Break,
			UcdProperty.STerm,
			UcdProperty.Terminal_Punctuation,
			UcdProperty.Titlecase_Mapping,
			UcdProperty.Unicode_1_Name,
			UcdProperty.Uppercase,
			UcdProperty.Uppercase_Mapping,
			UcdProperty.Word_Break,
			UcdProperty.XID_Continue,
			UcdProperty.XID_Start,
			// moved
			UcdProperty.Expands_On_NFC,
			UcdProperty.Expands_On_NFD,
			UcdProperty.Expands_On_NFKC,
			UcdProperty.Expands_On_NFKD,
			UcdProperty.FC_NFKC_Closure,

			UcdProperty.kAccountingNumeric,
			UcdProperty.kMandarin,
			UcdProperty.kOtherNumeric,
			UcdProperty.kPrimaryNumeric,
			UcdProperty.kRSUnicode,
			UcdProperty.kTotalStrokes
			);

	public static final EnumSet<UcdProperty> NORMATIVE_PROPERTY = EnumSet.of(
			UcdProperty.Age,
			UcdProperty.ASCII_Hex_Digit,
			UcdProperty.Bidi_Class,
			UcdProperty.Bidi_Control,
			UcdProperty.Bidi_Mirrored,
			UcdProperty.Block,
			UcdProperty.Canonical_Combining_Class,
			UcdProperty.Case_Folding,
			UcdProperty.Composition_Exclusion,
			UcdProperty.Decomposition_Mapping,
			UcdProperty.Decomposition_Type,
			UcdProperty.Default_Ignorable_Code_Point,
			UcdProperty.Deprecated,
			UcdProperty.Full_Composition_Exclusion,
			UcdProperty.General_Category,
			UcdProperty.Grapheme_Base,
			UcdProperty.Grapheme_Extend,
			UcdProperty.Hangul_Syllable_Type,
			UcdProperty.IDS_Binary_Operator,
			UcdProperty.IDS_Trinary_Operator,
			UcdProperty.Join_Control,
			UcdProperty.Joining_Group,
			UcdProperty.Joining_Type,
			UcdProperty.Line_Break,
			UcdProperty.Logical_Order_Exception,
			UcdProperty.Name,
			UcdProperty.Name_Alias,
			UcdProperty.NFC_Quick_Check,
			UcdProperty.NFD_Quick_Check,
			UcdProperty.NFKC_Quick_Check,
			UcdProperty.NFKD_Quick_Check,
			UcdProperty.Noncharacter_Code_Point,
			UcdProperty.Numeric_Type,
			UcdProperty.Numeric_Value,
			UcdProperty.Pattern_Syntax,
			UcdProperty.Pattern_White_Space,
			UcdProperty.Radical,
			UcdProperty.Simple_Case_Folding,
			UcdProperty.Simple_Lowercase_Mapping,
			UcdProperty.Simple_Titlecase_Mapping,
			UcdProperty.Simple_Uppercase_Mapping,
			UcdProperty.Soft_Dotted,
			UcdProperty.Unified_Ideograph,
			UcdProperty.Variation_Selector,
			UcdProperty.White_Space,
			// Unihan
			UcdProperty.kCompatibilityVariant,
			UcdProperty.kIICore,
			UcdProperty.kIRG_GSource,
			UcdProperty.kIRG_HSource,
			UcdProperty.kIRG_JSource,
			UcdProperty.kIRG_KPSource,
			UcdProperty.kIRG_KSource,
			UcdProperty.kIRG_MSource,
			UcdProperty.kIRG_TSource,
			UcdProperty.kIRG_USource,
			UcdProperty.kIRG_VSource
			);

	public static final EnumSet<UcdProperty> IMMUTABLE_PROPERTY = EnumSet.of(
			UcdProperty.Name,
			UcdProperty.Jamo_Short_Name,
			UcdProperty.Canonical_Combining_Class,
			UcdProperty.Decomposition_Mapping,
			UcdProperty.Pattern_Syntax,
			UcdProperty.Pattern_White_Space
			);

	public static final EnumSet<UcdProperty> PROVISIONAL_PROPERTY;

	static {
		final EnumSet<UcdProperty> temp2 = EnumSet.allOf(UcdProperty.class);
		temp2.removeAll(NORMATIVE_PROPERTY);
		temp2.removeAll(INFORMATIVE_PROPERTY);
		temp2.removeAll(CONTRIBUTORY_PROPERTY);
		PROVISIONAL_PROPERTY = temp2;

		if (CHECK_PROPERTY_STATUS) {
			final EnumSet<UcdProperty>[] sets = new EnumSet[] {
					IMMUTABLE_PROPERTY,
					NORMATIVE_PROPERTY, INFORMATIVE_PROPERTY, PROVISIONAL_PROPERTY, CONTRIBUTORY_PROPERTY,
					DEPRECATED_PROPERTY, STABLIZED_PROPERTY, OBSOLETE_PROPERTY};
			final String[] names = {
					"IMMUTABLE_PROPERTY",
					"NORMATIVE_PROPERTY", "INFORMATIVE_PROPERTY", "PROVISIONAL_PROPERTY", "CONTRIBUTORY_PROPERTY",
					"DEPRECATED_PROPERTY", "STABLIZED_PROPERTY", "OBSOLETE_PROPERTY"};

			final EnumSet<UcdProperty> temp = EnumSet.noneOf(UcdProperty.class);
			for (int i = 0; i < sets.length; ++i) {
				final EnumSet<UcdProperty> item = sets[i];
				final String name = names[i];
				for (int j = i+1; j < sets.length; ++j) {
					final EnumSet<UcdProperty> item2 = sets[j];
					final String name2 = names[j];
					if (item.containsAll(item2)) {
						System.out.println(name + "\tcontains\t" + name2);
					} else if (item2.containsAll(item)) {
						System.out.println(name2 + "\tcontains\t" + name);
					} else {
						temp.clear();
						temp.addAll(item);
						temp.retainAll(item2);
						if (temp.size() != 0) {
							System.out.println(name + "\tintersects\t" + name2 + "\t" + temp);
						}
					}
				}
			}
			throw new IllegalArgumentException();
		}
	}

	public enum PropertyStatus {Immutable, Normative, Informative, Provisional, Contributory, Deprecated, Stabilized, Obsolete}

	public static PropertyStatus getPropertyStatus(UcdProperty prop) {
		if (OBSOLETE_PROPERTY.contains(prop)) {
			return PropertyStatus.Obsolete;
		} else if (STABLIZED_PROPERTY.contains(prop)) {
			return PropertyStatus.Stabilized;
		} else if (DEPRECATED_PROPERTY.contains(prop)) {
			return PropertyStatus.Deprecated;
		} else if (CONTRIBUTORY_PROPERTY.contains(prop)) {
			return PropertyStatus.Contributory;
		} else if (INFORMATIVE_PROPERTY.contains(prop)) {
			return PropertyStatus.Informative;
		} else if (IMMUTABLE_PROPERTY.contains(prop)) {
			return PropertyStatus.Immutable;
		} else if (NORMATIVE_PROPERTY.contains(prop)) {
			return PropertyStatus.Normative;
		}
		return PropertyStatus.Provisional;
	}

	public static EnumSet<PropertyStatus> getPropertyStatusSet(UcdProperty prop) {
		final EnumSet<PropertyStatus> result = EnumSet.noneOf(PropertyStatus.class);
		if (OBSOLETE_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Obsolete);
		}
		if (STABLIZED_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Stabilized);
		}
		if (DEPRECATED_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Deprecated);
		}
		if (CONTRIBUTORY_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Contributory);
		}
		if (INFORMATIVE_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Informative);
		}
		if (IMMUTABLE_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Immutable);
		}
		if (NORMATIVE_PROPERTY.contains(prop)) {
			result.add(PropertyStatus.Normative);
		}
		// Normative or Informative or Contributory or Provisional.
		if (!(result.contains(PropertyStatus.Normative)
				|| result.contains(PropertyStatus.Informative)
				|| result.contains(PropertyStatus.Contributory))) {
			result.add(PropertyStatus.Provisional);
		}
		return result;
	}

	enum DefaultValueType {
		LITERAL(null),
		NONE(null),
		CODE_POINT(null),
		Script(UcdProperty.Script),
		Script_Extensions(UcdProperty.Script_Extensions),
		Simple_Lowercase_Mapping(UcdProperty.Simple_Lowercase_Mapping),
		Simple_Titlecase_Mapping(UcdProperty.Simple_Titlecase_Mapping),
		Simple_Uppercase_Mapping(UcdProperty.Simple_Uppercase_Mapping);
		static final HashMap<String, DefaultValueType> mapping = new HashMap<String, DefaultValueType>();
		static {
			mapping.put("<none>", NONE);
			mapping.put("<slc>", Simple_Lowercase_Mapping);
			mapping.put("<stc>", Simple_Titlecase_Mapping);
			mapping.put("<suc>", Simple_Uppercase_Mapping);
			mapping.put("<codepoint>", CODE_POINT);
			mapping.put("<code point>", CODE_POINT);
			mapping.put("<script>", Script);
			//mapping.put("NaN", LITERAL);
		}
		final UcdProperty property;
		static DefaultValueType forString(String string) {
			final DefaultValueType result = mapping.get(string);
			return result == null ? LITERAL : result;
		}
		DefaultValueType(UcdProperty prop) {
			property = prop;
		}
	}
	static VariableReplacer vr = new VariableReplacer();

	enum FileType {Field, HackField, PropertyValue, List, CJKRadicals, NamedSequences, NameAliases, StandardizedVariants}
	enum SpecialProperty {None, Skip1FT, Skip1ST, SkipAny4, Rational}
	enum ValueElements {
		Singleton, Unordered, Ordered;
		public boolean isBreakable(String string) {
			return (this == ValueElements.Unordered || this == ValueElements.Ordered);
		}
	}
	static Map<String,ValueElements> toMultiValued = new HashMap();
	static {
		toMultiValued.put("N/A", ValueElements.Singleton);
		toMultiValued.put("space", ValueElements.Singleton);
		toMultiValued.put("SINGLE_VALUED", ValueElements.Singleton);
		toMultiValued.put("EXTENSIBLE", ValueElements.Singleton);
		toMultiValued.put("MULTI_VALUED", ValueElements.Unordered);
		for (final ValueElements multi : ValueElements.values()) {
			toMultiValued.put(multi.toString(), multi);
			toMultiValued.put(UCharacter.toUpperCase(multi.toString()), multi);
		}
	}

	static final Joiner JOIN = new Joiner(FIELD_SEPARATOR);

	static class PropertyParsingInfo implements Comparable<PropertyParsingInfo>{
		final String file;
		final UcdProperty property;
		final int fieldNumber;
		final SpecialProperty special;
		String defaultValue;
		DefaultValueType defaultValueType = DefaultValueType.LITERAL;
		//UnicodeMap<String> data;
		//final Set<String> errors = new LinkedHashSet<String>();
		Pattern regex = null;
		ValueElements multivalued = ValueElements.Singleton;
		public String originalRegex;

		public PropertyParsingInfo(String... propertyInfo) {
			if (propertyInfo.length < 2 || propertyInfo.length > 4) {
				throw new IllegalArgumentException("Must have 2 to 4 args: " + Arrays.asList(propertyInfo));
			}
			file = propertyInfo[0];

			property = UcdProperty.forString(propertyInfo[1]);
			int temp = 1;
			if (propertyInfo.length > 2 && !propertyInfo[2].isEmpty()) {
				//try {
				temp = Integer.parseInt(propertyInfo[2]);
				//                } catch (Exception e) {
				//                    temp = temp;
				//                }
			}
			fieldNumber = temp;
			special = propertyInfo.length < 4 || propertyInfo[3].isEmpty()
					? SpecialProperty.None
							: SpecialProperty.valueOf(propertyInfo[3]);
		}
		@Override
		public String toString() {
			return file + " ;\t"
					+ property + " ;\t"
					+ fieldNumber + " ;\t"
					+ special + " ;\t"
					+ defaultValue + " ;\t"
					+ defaultValueType + " ;\t"
					+ multivalued + " ;\t"
					+ regex + " ;\t"
					;
		}
		@Override
		public int compareTo(PropertyParsingInfo arg0) {
			int result;
			if (0 != (result = file.compareTo(arg0.file))) {
				return result;
			}
			if (0 != (result = property.toString().compareTo(arg0.property.toString()))) {
				return result;
			}
			return fieldNumber - arg0.fieldNumber;
		}

		public void put(UnicodeMap<String> data, IntRange intRange, String string, Merge<String> merger) {
			put(data, intRange, string, merger, false);
		}

		public static final Normalizer2 NFD = Normalizer2.getNFDInstance();
		public static final Normalizer2 NFC = Normalizer2.getNFCInstance();

		public void put(UnicodeMap<String> data, IntRange intRange, String string, Merge<String> merger, boolean hackHangul) {
			if (string != null && string.isEmpty() && property != UcdProperty.NFKC_Casefold) {
				string = null;
			}
			string = normalizeAndVerify(string);
			if (intRange.string != null) {
				PropertyUtilities.putNew(data, intRange.string, string, merger);
			} else {
				for (int codepoint = intRange.start; codepoint <= intRange.end; ++codepoint) {
					try {
						if (hackHangul) {
							String fullDecomp = NFD.getDecomposition(codepoint); // use ICU for Hangul decomposition
							if (fullDecomp.length() > 2) {
								fullDecomp = NFC.normalize(fullDecomp.substring(0,2)) + fullDecomp.substring(2);
							}
							PropertyUtilities.putNew(data, codepoint, fullDecomp, merger);
						} else if (string == CONSTRUCTED_NAME) {
							PropertyUtilities.putNew(data, codepoint, UCharacter.getName(codepoint), merger); // use ICU for Hangul Name construction, constant
						} else {
							PropertyUtilities.putNew(data, codepoint, string, merger);
						}
					} catch (final Exception e) {
						throw new IllegalArgumentException(property + ":\t" + intRange.start + "..." + intRange.end + "\t" + string);
					}
				}
			}
		}

		public String normalizeAndVerify(String string) {
			switch (property.getType()) {
			case Enumerated:
			case Catalog:
			case Binary:
				string = normalizeEnum(string);
				break;
			case Numeric:
			case Miscellaneous:
				if (property==UcdProperty.Script_Extensions) {
					string = normalizeEnum(string);
				} else {
					checkRegex2(string);
				}
				break;
			case String:
				// check regex
				checkRegex2(string);
				if (string == null) {
					// nothing
				} else {
					string = Utility.fromHex(string);
				}
				break;
			}
			return string;
		}

		public String normalizeEnum(String string) {
			if (multivalued.isBreakable(string)) {
				final PropertyParsingInfo propInfo = property == UcdProperty.Script_Extensions ? getPropertyInfo(UcdProperty.Script) : this;
				if (string.contains(" ")) {
					final StringBuilder newString = new StringBuilder();
					for (final String part : SPACE.split(string)) {
						if (newString.length() != 0) {
							newString.append(' ');
						}
						newString.append(propInfo.checkEnum(part));
					}
					string = newString.toString();
				} else {
					string = propInfo.checkEnum(string);
				}
			} else {
				string = checkEnum(string);
			}
			return string;
		}

		public void checkRegex2(String string) {
			if (regex == null) {
				DATA_LOADING_ERRORS.put(property, "Regex missing");
				return;
			}
			if (string == null) {
				return;
			}
			if (multivalued.isBreakable(string) && string.contains(" ")) {
				for (final String part : SPACE.split(string)) {
					checkRegex(part);
				}
			} else {
				checkRegex(string);
			}
		}
		public String checkEnum(String string) {
			final Enum item = property.getEnum(string);
			if (item == null) {
				final String errorMessage = property + "\tBad enum value:\t" + string;
				DATA_LOADING_ERRORS.put(property, errorMessage);
			} else {
				string = item.toString();
			}
			return string;
		}

		public void checkRegex(String part) {
			if (!regex.matcher(part).matches()) {
				final String part2 = NFD.normalize(part);
				if (!regex.matcher(part2).matches()) {
					DATA_LOADING_ERRORS.put(property, "Regex failure: " + RegexUtilities.showMismatch(regex, part));
				}
			}
		}
		public void put(UnicodeMap<String> data, IntRange intRange, String string) {
			put(data, intRange, string, null);
		}
	}

	static Map<String, IndexUnicodeProperties> version2IndexUnicodeProperties = new HashMap();

	private static EnumMap<UcdProperty, PropertyParsingInfo> property2PropertyInfo = new EnumMap<UcdProperty,PropertyParsingInfo>(UcdProperty.class);

	public static PropertyParsingInfo getPropertyInfo(UcdProperty property) {
		return property2PropertyInfo.get(property);
	}

	private static Relation<String,PropertyParsingInfo> file2PropertyInfoSet = Relation.of(new HashMap<String,Set<PropertyParsingInfo>>(), HashSet.class);

	static Map<String,FileType> file2Type = new HashMap<String,FileType>();

	static {
		final Matcher semicolon = SEMICOLON.matcher("");
		for (final String line : FileUtilities.in(IndexUnicodeProperties.class, "IndexUnicodeProperties.txt")) {
			if (line.startsWith("#") || line.isEmpty()) {
				continue;
			}

			final String[] parts = Regexes.split(semicolon, line.trim());
			// file ; property name ; derivation info
			//System.out.println(Arrays.asList(parts));
			if (parts[0].equals("FileType")) {
				PropertyUtilities.putNew(file2Type, parts[1], FileType.valueOf(parts[2]));
			} else {
				final UcdProperty prop = UcdProperty.forString(parts[1]);
				final PropertyParsingInfo propertyInfo = new PropertyParsingInfo(parts);
				try {
					PropertyUtilities.putNew(property2PropertyInfo, prop, propertyInfo);
					getFile2PropertyInfoSet().put(parts[0], propertyInfo);
				} catch (final Exception e) {
					throw new IllegalArgumentException("Can't find property for <" + parts[1] + ">", e);
				}
			}
		}
		// DO THESE FIRST (overrides values in files!)
		for (final String file : Arrays.asList("ExtraPropertyAliases.txt","ExtraPropertyValueAliases.txt")) {
			for (final String line : FileUtilities.in(IndexUnicodeProperties.class, file)) {
				handleMissing(FileType.PropertyValue, null, line);
			}
		}
		for (final String line : FileUtilities.in("", Utility.getMostRecentUnicodeDataFile("PropertyValueAliases", Default.ucdVersion(), true, true))) {
			handleMissing(FileType.PropertyValue, null, line);
		}
		for (final String line : FileUtilities.in(IndexUnicodeProperties.class, "IndexPropertyRegex.txt")) {
			getRegexInfo(line);
		}

		//        for (String line : FileUtilities.in(IndexUnicodeProperties.class, "Multivalued.txt")) {
		//            UcdProperty prop = UcdProperty.forString(line);
		//            PropertyParsingInfo propInfo = property2PropertyInfo.get(prop);
		//            propInfo.multivalued = Multivalued.MULTI_VALUED;
		//        }

		//        for (UcdProperty x : UcdProperty.values()) {
		//            if (property2PropertyInfo.containsKey(x.toString())) continue;
		//            if (SHOW_PROP_INFO) System.out.println("Missing: " + x);
		//        }
	}

	public static void getRegexInfo(String line) {
		try {
			if (line.startsWith("$")) {
				final String[] parts = EQUALS.split(line);
				vr.add(parts[0], vr.replace(parts[1]));
				return;
			}
			if (line.isEmpty() || line.startsWith("#")) {
				return;
			}
			// have to do this painfully, since the regex may contain semicolons
			final Matcher m = SEMICOLON.matcher(line);
			if (!m.find()) {
				throw new IllegalArgumentException("Bad semicolons in: " + line);
			}
			final String propName = line.substring(0, m.start());
			final int propNameEnd = m.end();
			if (!m.find()) {
				throw new IllegalArgumentException("Bad semicolons in: " + line);
			}
			final UcdProperty prop = UcdProperty.forString(propName);
			final PropertyParsingInfo propInfo = property2PropertyInfo.get(prop);
			final String multivalued = line.substring(propNameEnd, m.start());
			propInfo.multivalued = toMultiValued.get(multivalued);
			if (propInfo.multivalued == null) {
				throw new IllegalArgumentException("Bad multivalued in: " + line);
			}
			String regex = line.substring(m.end());
			propInfo.originalRegex = regex;
			regex = vr.replace(regex);
			//        if (!regex.equals(propInfo.originalRegex)) {
			//            regex = vr.replace(propInfo.originalRegex);
			//            System.out.println(propInfo.originalRegex + "=>" + regex);
			//        }
			if (regex.equals("null")) {
				propInfo.regex = null;
			} else {
				propInfo.regex = hackCompile(regex);
			}
		} catch (final Exception e) {
			throw new IllegalArgumentException(line, e);
		}
	}

	public static Pattern hackCompile(String regex) {
		if (regex.contains("\\x")) {
			final StringBuilder builder = new StringBuilder();
			final Matcher m = SLASHX.matcher(regex);
			int start = 0;
			while (m.find()) {
				builder.append(regex.substring(start, m.start()));
				final int codePoint = Integer.parseInt(m.group(1),16);
				//System.out.println("\\x char:\t" + new StringBuilder().appendCodePoint(codePoint));
				builder.appendCodePoint(codePoint);
				start = m.end();
			}
			builder.append(regex.substring(start));
			regex = builder.toString();
		}
		return Pattern.compile(regex);
	}

	private IndexUnicodeProperties(String ucdVersion2) {
		ucdVersion = ucdVersion2;
		oldVersion = ucdVersion2.compareTo("6.3.0") < 0;
	}

	public static final IndexUnicodeProperties make(String ucdVersion) {
		IndexUnicodeProperties newItem = version2IndexUnicodeProperties.get(ucdVersion);
		if (newItem == null) {
			version2IndexUnicodeProperties.put(ucdVersion, newItem = new IndexUnicodeProperties(ucdVersion));
		}
		return newItem;
	}

	final String ucdVersion;
	final boolean oldVersion;
	final EnumMap<UcdProperty, UnicodeMap<String>> property2UnicodeMap = new EnumMap<UcdProperty, UnicodeMap<String>>(UcdProperty.class);
	final Set<String> fileNames = new TreeSet<String>();
	private final Map<UcdProperty, Long> cacheFileSize = new EnumMap<UcdProperty, Long>(UcdProperty.class);

	public Map<UcdProperty, Long> getCacheFileSize() {
		return Collections.unmodifiableMap(cacheFileSize);
	}

	static final Transform<String, String>    fromNumericPinyin   = Transliterator.getInstance("NumericPinyin-Latin;nfc");

	private static final Merge<String> ALPHABETIC_JOINER = new Merge<String>() {
		TreeSet<String> sorted = new TreeSet<String>();
		@Override
		public String merge(String first, String second) {
			sorted.clear();
			sorted.addAll(Arrays.asList(first.split(FIELD_SEPARATOR)));
			sorted.addAll(Arrays.asList(second.split(FIELD_SEPARATOR)));
			return CollectionUtilities.join(sorted, FIELD_SEPARATOR);
		}

	};

	public UnicodeMap<String> load(UcdProperty prop2) {
		UnicodeMap<String> data0 = property2UnicodeMap.get(prop2);
		if (data0 != null) {
			return data0;
		}

		final PropertyParsingInfo fileInfo = property2PropertyInfo.get(prop2);
		final String fullFilename = Utility.getMostRecentUnicodeDataFile(fileInfo.file, ucdVersion, true, false);

		if (FILE_CACHE) {
			data0 = getCachedMap(prop2, fullFilename);
			if (data0 != null) {
				property2UnicodeMap.put(prop2, data0.freeze());
				return data0;
			}
		}

		final Set<PropertyParsingInfo> propInfoSet = getFile2PropertyInfoSet().get(fileInfo.file);

		FileType fileType = file2Type.get(fileInfo.file);
		if (fileType == null) {
			fileType = FileType.Field;
		}

		for (final PropertyParsingInfo propInfo : propInfoSet) {
			PropertyUtilities.putNew(property2UnicodeMap, propInfo.property, new UnicodeMap<String>());
		}

		// if a file is not in a given version of Unicode, we skip it.
		if (fullFilename == null) {
			if (SHOW_LOADED) {
				System.out.println("Version\t" + ucdVersion + "\tFile doesn't exist: " + fileInfo.file);
			}
		} else {
			fileNames.add(fullFilename);

			final Matcher semicolon = SEMICOLON.matcher("");
			final Matcher tab = TAB.matcher("");
			final IntRange intRange = new IntRange();
			int lastCodepoint = 0;

			int lineCount = 0;
			boolean containsEOF = false;
			Merge<String> merger = null;

			for (String line : FileUtilities.in("", fullFilename)) {
				++lineCount;
				if (line.contains("F900")) {
					final int y = 3;
				}
				final int hashPos = line.indexOf('#');
				if (hashPos >= 0) {
					if (line.contains("# EOF")) {
						containsEOF = true;
					} else {
						handleMissing(fileType, propInfoSet, line);
					}
					line = line.substring(0,hashPos);
				}
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				//HACK
				final String[] parts = Regexes.split(line.startsWith("U+") ? tab : semicolon, line.trim());
				//System.out.println(line + "\t\t" + Arrays.asList(parts));
				// HACK RANGE
				// 3400;<CJK Ideograph Extension A, First>;Lo;0;L;;;;;N;;;;;
				// 4DB5;<CJK Ideograph Extension A, Last>;Lo;0;L;;;;;N;;;;;
				// U+4F70   kAccountingNumeric  100
				/*
				 * CJKRadicals The first field is the # radical number. The second
				 * field is the CJK Radical character. The third # field is the CJK
				 * Unified Ideograph. 1; 2F00; 4E00
				 */
				if (fileType != FileType.CJKRadicals && fileType != FileType.NamedSequences) {
					intRange.set(parts[0]);
				}
				boolean hackHangul = false;

				switch(fileType) {
				case CJKRadicals: {
					final PropertyParsingInfo propInfo = property2PropertyInfo.get(UcdProperty.CJK_Radical);
					final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
					intRange.set(parts[1]);
					propInfo.put(data, intRange, parts[0]);
					intRange.set(parts[2]);
					propInfo.put(data, intRange, parts[0]);
					break;
				}
				case NamedSequences: {
					for (final PropertyParsingInfo propInfo : propInfoSet) {
						final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
						intRange.set(parts[1]);
						propInfo.put(data, intRange, parts[0]);
					}
					break;
				}
				case PropertyValue: {
					final PropertyParsingInfo propInfo = property2PropertyInfo.get(UcdProperty.forString(parts[1]));
					final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
					if (!propInfoSet.contains(propInfo)) {
						throw new IllegalArgumentException("Property not listed for file: " + propInfo);
					}
					switch(propInfo.property.getType()) {
					case Binary:
						propInfo.put(data, intRange, "Yes"); break;
					default:
						String value = parts[2];
						if (propInfo.property == UcdProperty.kMandarin) {
							if (oldVersion) {
								value = fromNumericPinyin.transform(value.toLowerCase(Locale.ENGLISH));
							}
						}
						propInfo.put(data, intRange, value); break;
					}
					break;
				}
				case StandardizedVariants:
					if (!parts[2].isEmpty()) {
						parts[1] = parts[1] + "(" + parts[2] + ")";
					}
					//$FALL-THROUGH$
				case NameAliases:
					merger = ALPHABETIC_JOINER;
					//$FALL-THROUGH$
				case HackField:
					if (parts[1].endsWith("Last>")) {
						intRange.start = lastCodepoint + 1;
					}
					if (parts[1].startsWith("<")) {
						if (parts[1].contains("Ideograph")) {
							parts[1] = "CJK UNIFIED IDEOGRAPH-#";
						} else if (parts[1].contains("Hangul Syllable")) {
							parts[1] = CONSTRUCTED_NAME;
							hackHangul = true;
						} else {
							parts[1] = null;
						}
					} else if (parts[1].startsWith("CJK COMPATIBILITY IDEOGRAPH-")) {
						parts[1] = "CJK COMPATIBILITY IDEOGRAPH-#"; // hack for uniform data
					}
					lastCodepoint = intRange.end;
					//$FALL-THROUGH$
				case Field:
				{
					for (final PropertyParsingInfo propInfo : propInfoSet) {
						final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
						String string = parts[propInfo.fieldNumber];
						switch(propInfo.special) {
						case None:
							break;
						case Rational:
							//                            int slashPos = string.indexOf('/');
							//                            double rational;
							//                            if (slashPos < 0) {
							//                                rational = Double.parseDouble(string);
							//                            } else {
							//                                rational = Double.parseDouble(string.substring(0,slashPos)) / Double.parseDouble(string.substring(slashPos+1));
							//                            }
							//                            string = Double.toString(rational);
							break;
						case Skip1ST:
							if ("ST".contains(parts[1])) {
								continue;
							}
							break;
						case Skip1FT:
							if ("FT".contains(parts[1])) {
								continue;
							}
							break;
						case SkipAny4:
							if (!parts[4].isEmpty()) {
								continue;
							}
							break;
						default: throw new IllegalArgumentException();
						}
						if (fileType == FileType.HackField && propInfo.fieldNumber == 5) { // remove decomposition type
							final int dtEnd = string.indexOf('>');
							if (dtEnd >= 0) {
								string = string.substring(dtEnd + 1).trim();
							}
						}
						propInfo.put(data, intRange, string, merger, hackHangul && propInfo.property == UcdProperty.Decomposition_Mapping);
					}
					merger = null;
					break;
				}
				case List: {
					if (propInfoSet.size() != 1) {
						throw new IllegalArgumentException("List files must have only one property, and must be Boolean");
					}
					final PropertyParsingInfo propInfo = propInfoSet.iterator().next();
					final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
					//prop.data.putAll(UnicodeSet.ALL_CODE_POINTS, "No");
					propInfo.put(data, intRange, "Yes");
					break;
				}
				default: throw new IllegalArgumentException();
				}
			}
			if (SHOW_LOADED) {
				System.out.println("Version\t" + ucdVersion + "\tLoaded: " + fileInfo.file + "\tlines: " + lineCount + (containsEOF ? "" : "\t*NO '# EOF'"));
			}
		}
		for (final PropertyParsingInfo propInfo : propInfoSet) {
			final UnicodeMap<String> data = property2UnicodeMap.get(propInfo.property);
			final UnicodeSet nullValues = data.getSet(null);
			//            if (propInfo.defaultValue == null) {
			//                if (CHECK_MISSING != null) {
			//                    System.out.println("** Clearing null dv in " + propInfo.property);
			//                }
			//                propInfo.defaultValue = "<none>";
			//            }
			switch (propInfo.defaultValueType) {
			case Script: case Simple_Lowercase_Mapping: case Simple_Titlecase_Mapping: case Simple_Uppercase_Mapping:
				final UcdProperty sourceProp = propInfo.defaultValueType.property;
				final UnicodeMap<String> otherMap = load(sourceProp); // recurse
				for (final String cp : nullValues) {
					data.put(cp, otherMap.get(cp));
				}
				// propInfo.defaultValueType = property2PropertyInfo.get(sourceProp).defaultValueType; // reset to the type
				break;
			case LITERAL:
				data.putAll(nullValues, propInfo.defaultValue);
				break;
			case NONE:
				//data.putAll(nullValues, propInfo.defaultValue);
				// do nothing, already none;
				break;
			case CODE_POINT:
				// requires special handling later
				break;
			default:
				throw new IllegalArgumentException(); // unexpected error
			}
			data.freeze();
			if (FILE_CACHE) {
				storeCachedMap(propInfo.property, data);
			}
		}
		return property2UnicodeMap.get(prop2);
	}

	static final String CACHE_DIR = Utility.GEN_DIR + "BIN/";

	private void storeCachedMap(UcdProperty prop2, UnicodeMap<String> data) {
		try {
			final String cacheFileDirName = CACHE_DIR + ucdVersion;
			final File cacheFileDir = new File(cacheFileDirName);
			if (!cacheFileDir.exists()) {
				cacheFileDir.mkdir();
			}
			final String cacheFileName = cacheFileDirName + "/" + prop2 + ".bin";
			final FileOutputStream fos = new FileOutputStream(cacheFileName);
			final OutputStream gz = GZIP ? new GZIPOutputStream(fos) : fos;
			final DataOutputStream out = new DataOutputStream(gz);
			final ItemWriter<String> stringWriter = new UnicodeDataOutput.StringWriter();
			if (SIMPLE_COMPRESSION) {
				final UnicodeDataOutput unicodeDataOutput = new UnicodeDataOutput();
				unicodeDataOutput.set(out, true).writeUnicodeMap(data, stringWriter);
			} else {
				UnicodeDataOutput.writeUnicodeMap(data, stringWriter, out);
			}
			out.flush();
			out.close();
			gz.close();
			fos.close();
			cacheFileSize.put(prop2, new File(cacheFileName).length());
		} catch (final IOException e) {
			throw new IllegalArgumentException(e);
		}
		// for verification
		final UnicodeMap<String> dup = getCachedMap(prop2, null);
		if (!data.equals(dup)) {
			throw new IllegalArgumentException("Failed storage");
		}
	}

	/**
	 * Return null if there is no cached version.
	 * @param prop2
	 * @param sourceFileName
	 * @return
	 */
	private UnicodeMap<String> getCachedMap(UcdProperty prop2, String sourceFileName) {
		FileInputStream fis;
		File cacheFile;
		try {
			final String cacheFileName = CACHE_DIR + ucdVersion + "/" + prop2 + ".bin";
			cacheFile = new File(cacheFileName);
			// if the source file is older than the cached, skip
			if (sourceFileName != null) {
				final File sourceFile = new File(sourceFileName);
				if (sourceFile.lastModified() > cacheFile.lastModified()) {
					return null;
				}
			}
			fis = new FileInputStream(cacheFile);
			cacheFileSize.put(prop2, cacheFile.length());
		} catch (final Exception e) {
			return null;
		}
		try {
			final InputStream gs = GZIP ? new GZIPInputStream(fis) : fis;
			final DataInputStream in = new DataInputStream(gs);
			final ItemReader<String> stringReader = new UnicodeDataInput.StringReader();
			UnicodeMap<String> newItem;
			if (SIMPLE_COMPRESSION) {
				final UnicodeDataInput unicodeDataInput = new UnicodeDataInput();
				newItem = unicodeDataInput.set(in, true).readUnicodeMap(stringReader);
			} else {
				newItem = UnicodeDataInput.readUnicodeMap(stringReader, in);
			}
			in.close();
			gs.close();
			fis.close();
			cacheFileSize.put(prop2, cacheFile.length());
			return newItem;
		} catch (final IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static void handleMissing(FileType fileType, Set<PropertyParsingInfo> propInfoSet, String missing) {
		if (!missing.contains("@missing")) { // quick test
			return;
		}
		final Matcher simpleMissingMatcher = SIMPLE_MISSING_PATTERN.matcher(missing);
		if (!simpleMissingMatcher.lookingAt()) {
			//            if (missing.contains("@missing")) {
			//                System.out.println("Skipping " + missing + "\t" + RegexUtilities.showMismatch(simpleMissingMatcher, missing));
			//            }
			return;
		}
		final Matcher missingMatcher = MISSING_PATTERN.matcher(missing);
		if (!missingMatcher.matches()) {
			System.out.println(RegexUtilities.showMismatch(MISSING_PATTERN, missing));
			throw new IllegalArgumentException("Bad @missing statement: " + missing);
		}
		final boolean isEmpty = missingMatcher.group(1).equals("empty");
		final int start = Integer.parseInt(missingMatcher.group(2),16);
		final int end = Integer.parseInt(missingMatcher.group(3),16);
		if (start != 0 || end != 0x10FFFF) {
			System.out.println("Unexpected range: " + missing);
		}
		// # @missing: 0000..10FFFF; cjkIRG_KPSource; <none>

		String value1 = missingMatcher.group(4);
		String value2 = missingMatcher.group(5);
		String value3 = missingMatcher.group(6);
		if (value1 != null) {
			value1 = value1.trim();
		}
		if (value2 != null) {
			value2 = value2.trim();
		}
		if (value3 != null) {
			value3 = value3.trim();
		}

		switch (fileType) {
		case Field: {
			for (final PropertyParsingInfo propInfo : propInfoSet) {
				final String value = propInfo.fieldNumber == 1 ? value1
						: propInfo.fieldNumber == 2 ? value2
								: value3;
				setPropDefault(propInfo.property, value, missing, isEmpty);
			}
			break;
		}
		case PropertyValue: {
			final UcdProperty ucdProp = UcdProperty.forString(value1);
			final PropertyParsingInfo propInfo = property2PropertyInfo.get(ucdProp);
			setPropDefault(propInfo.property, value2, missing, isEmpty);
			break;
		}
		default:
			System.out.println("Unhandled missing line: " + missing);
		}
	}

	public static void setPropDefault(UcdProperty prop, String value, String line, boolean isEmpty) {
		if (prop == CHECK_MISSING) {
			System.out.format("** %s %s %s %s\n", prop, value, line, isEmpty);
		}
		final PropertyParsingInfo propInfo = property2PropertyInfo.get(prop);

		if (value != null && !value.startsWith("<")) {
			value = propInfo.normalizeAndVerify(value);
		}

		if (propInfo.defaultValue == null) {
			propInfo.defaultValueType = DefaultValueType.forString(value);
			propInfo.defaultValue = value;
			if (SHOW_DEFAULTS) {
				DATA_LOADING_ERRORS.put(prop, "**\t" + prop + "\t" + propInfo.defaultValueType + "\t" + propInfo.defaultValue);
			}
		} else if (propInfo.defaultValue.equals(value)) {
		} else {
			final String comment = "\t ** ERROR Will not change default for " + prop +
					" to «" + value + "», retaining " + propInfo.defaultValue;
			//            propInfo.defaultValueType = DefaultValueType.forString(value);
			//            propInfo.defaultValue = value;
			DATA_LOADING_ERRORS.put(prop, comment);
		}
	}

	// # @missing: 0000..10FFFF; cjkIRG_KPSource; <none>
	// # @missing: 0000..10FFFF; Other

	static void setFile2PropertyInfoSet(Relation<String,PropertyParsingInfo> file2PropertyInfoSet) {
		IndexUnicodeProperties.file2PropertyInfoSet = file2PropertyInfoSet;
	}

	static Relation<String,PropertyParsingInfo> getFile2PropertyInfoSet() {
		return file2PropertyInfoSet;
	}

	private static class IntRange {
		int start;
		int end;
		String string;
		public IntRange set(String source) {
			if (source.startsWith("U+")) {
				source = source.substring(2);
			}
			final int range = source.indexOf("..");
			if (range >= 0) {
				start = Integer.parseInt(source.substring(0,range),16);
				end = Integer.parseInt(source.substring(range+2),16);
				string = null;
			} else if (source.contains(" ")) {
				string = Utility.fromHex(source);
			} else {
				start = end = Integer.parseInt(source, 16);
				string = null;
			}
			return this;
		}
	}

	public static String getResolvedValue(IndexUnicodeProperties props, UcdProperty prop, String codepoint, String value) {
		if (value == null && props != null) {
			if (getResolvedDefaultValueType(prop) == DefaultValueType.CODE_POINT) {
				return codepoint;
			}
		}
		if (prop == UcdProperty.Name && value != null && value.endsWith("-#")) {
			return value.substring(0,value.length()-1) + Utility.hex(codepoint);
		}
		return value;
	}

	public static String getResolvedValue(IndexUnicodeProperties props, UcdProperty prop, int codepoint, String value) {
		if (value == null && props != null) {
			if (getResolvedDefaultValueType(prop) == DefaultValueType.CODE_POINT) {
				return UTF16.valueOf(codepoint);
			}
		}
		if (prop == UcdProperty.Name && value != null && value.endsWith("-#")) {
			return value.substring(0,value.length()-1) + Utility.hex(codepoint);
		}
		return value;
	}

	public static DefaultValueType getResolvedDefaultValueType(UcdProperty prop) {
		while (true) {
			final DefaultValueType result = property2PropertyInfo.get(prop).defaultValueType;
			if (result.property == null) {
				return result;
			}
			prop = result.property;
		}
	}

	public static String getDefaultValue(UcdProperty prop) {
		return property2PropertyInfo.get(prop).defaultValue;
	}

	public String getResolvedValue(UcdProperty prop, String codepoint) {
		return getResolvedValue(this, prop, codepoint, this.getRawValue(prop, codepoint));
	}

	public String getResolvedValue(UcdProperty prop, int codepoint) {
		return getResolvedValue(this, prop, codepoint, this.getRawValue(prop, codepoint));
	}

	public String getRawValue(UcdProperty ucdProperty, String codepoint) {
		return load(ucdProperty).get(codepoint);
	}

	public String getRawValue(UcdProperty ucdProperty, int codepoint) {
		return load(ucdProperty).get(codepoint);
	}

	public static String normalizeValue(UcdProperty property, String propertyValue) {
		final PropertyParsingInfo info = getPropertyInfo(property);
		propertyValue = info.normalizeEnum(propertyValue);
		return propertyValue;
	}

	//    static final class IndexUnicodeProperty extends UnicodeProperty.UnicodeMapProperty {
	//
	//        private PropertyNames names;
	//        private String defaultValue;
	//        boolean initialized = false;
	//
	//        IndexUnicodeProperty(PropertyNames names) {
	//            this.names = names;
	//        }
	//
	//        protected String _getValue(int codepoint) {
	//            String lastValue = _getValue(codepoint);
	//            if (lastValue == SpecialValues.CODEPOINT.toString()) {
	//                return UTF16.valueOf(codepoint);
	//            }
	//            if (lastValue == null) {
	//                return defaultValue;
	//            }
	//            return lastValue;
	//        }
	//
	//        public List _getNameAliases(List result) { // TODO fix interface
	//            result.addAll(names.getAllNames());
	//            return result;
	//        }
	//    }
}
