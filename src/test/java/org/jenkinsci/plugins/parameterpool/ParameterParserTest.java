package org.jenkinsci.plugins.parameterpool;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParameterParserTest {

    @Test
    public void parseSimpleValues() {
        ParameterParser processor = new ParameterParser("test1, test2, test3");
        assertEquals("test1, test2, test3", processor.valuesAsText());
    }

    @Test
    public void parseSimpleRange() {
        ParameterParser processor = new ParameterParser("test[1..3]");
        assertEquals("test1, test2, test3", processor.valuesAsText());
    }

    @Test
    public void parseRangeWithPrefixAndSuffix() {
        ParameterParser processor = new ParameterParser("test[1..3]Value, separate");
        assertEquals("test1Value, test2Value, test3Value, separate", processor.valuesAsText());
    }

    @Test
    public void parseReverseRange() {
        ParameterParser processor = new ParameterParser("qe-upgrade-vm-11, qe-upgrade-vm-[6..2], qe-upgrade-vm-0");
        assertEquals("qe-upgrade-vm-11, qe-upgrade-vm-6, qe-upgrade-vm-5, qe-upgrade-vm-4, " +
                "qe-upgrade-vm-3, qe-upgrade-vm-2, qe-upgrade-vm-0", processor.valuesAsText());
    }

    @Test
    public void parseRangeWithSpecialCharacters() {
        ParameterParser processor = new ParameterParser("t!@#$%^&*()/.<4[3..1]");
        assertEquals("t!@#$%^&*()/.<43, t!@#$%^&*()/.<42, t!@#$%^&*()/.<41", processor.valuesAsText());
    }

    @Test
    public void parseNoValues() {
        assertEquals("", new ParameterParser(null).valuesAsText());
    }

}
