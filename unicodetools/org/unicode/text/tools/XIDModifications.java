package org.unicode.text.tools;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.text.UCD.IdentifierInfo.IdentifierStatus;
import org.unicode.text.UCD.IdentifierInfo.IdentifierType;
import org.unicode.text.utility.Utility;

import com.google.common.collect.ImmutableSet;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.util.VersionInfo;

public class XIDModifications {
    private UnicodeMap<IdentifierStatus> identifierStatus = new UnicodeMap<>();
    private UnicodeMap<Set<IdentifierType>> identifierType = new UnicodeMap<>();

    public XIDModifications(String directory) {
    	File dir = new File(directory);
    	String finalPart = dir.getName();
    	VersionInfo version = VersionInfo.getInstance(finalPart);
        identifierStatus.putAll(0,0x10FFFF, IdentifierStatus.restricted);
        if (version.getMajor() <= 8) {
            identifierType.putAll(0,0x10FFFF, Collections.singleton(IdentifierType.not_characters));
            new MyReader().process(directory, "xidmodifications.txt");
        } else {
            // Version 9.0.0+: Everything that isn't in IdentifierType.txt is RECOMMENDED.
            identifierType.putAll(0,0x10FFFF, Collections.singleton(IdentifierType.recommended));
            new MyReaderType().process(directory, "IdentifierType.txt");
            new MyReaderStatus().process(directory, "IdentifierStatus.txt");
        }
        identifierStatus.freeze();
        identifierType.freeze();
    }

    public UnicodeMap<IdentifierStatus> getStatus() {
        return identifierStatus;
    }
    public UnicodeMap<Set<IdentifierType>> getType() {
        return identifierType;
    }

    private class MyReaderType extends FileUtilities.SemiFileReader {
        @Override
        protected boolean handleLine(int lineCount, int start, int end, String[] items) {
            ImmutableSet.Builder<IdentifierType> b = ImmutableSet.builder();
            for (String s : items[1].split(" ")) {
                b.add(IdentifierType.fromString(s));
            }
            identifierType.putAll(start, end, b.build());
            return true;
        }
    }
    private class MyReaderStatus extends FileUtilities.SemiFileReader {
        @Override
        protected boolean handleLine(int lineCount, int start, int end, String[] items) {
            identifierStatus.putAll(start, end, IdentifierStatus.fromString(items[1]));
            return true;
        }
    }

    private class MyReader extends FileUtilities.SemiFileReader {
        @Override
        protected boolean handleLine(int lineCount, int start, int end, String[] items) {
            identifierStatus.putAll(start, end, IdentifierStatus.fromString(items[1]));
            identifierType.putAll(start, end, Collections.singleton(IdentifierType.fromString(items[2])));
            return true;
        }
    }
}
