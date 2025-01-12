/*
 * Copyright 2018-2021 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.ena.webin.cli;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Assert;

public class XmlTester {
    public static void
    assertXml(String xml, String expectedXml) {
        xml = xml.replaceAll("<\\?xml.*", "");
        xml = xml.replaceAll("\\r\\n?", "\n");
        xml = Arrays.stream(xml.split("\n"))
                .filter( line -> !line.trim().isEmpty() )
                .map( line -> line.replaceAll("^\\s+", ""))
                .map( line -> line.replaceAll("\\s+$", ""))
                .collect(Collectors.joining("\n"));
        xml = xml.replaceAll("<\\s+", "<");
        xml = xml.replaceAll("\\s+/>", "/>");
        xml = xml.replaceAll("\\s*alias=\"[^\"]+\"", "");
        expectedXml = expectedXml.replaceAll("<\\?xml.*", "");
        expectedXml = expectedXml.replaceAll("\\r\\n?", "\n");
        expectedXml = Arrays.stream(expectedXml.split("\n"))
                .filter( line -> !line.trim().isEmpty() )
                .map( line -> line.replaceAll("^\\s+", ""))
                .map( line -> line.replaceAll("\\s+$", ""))
                .collect(Collectors.joining("\n"));
        expectedXml = expectedXml.replaceAll("<\\s+", "<");
        expectedXml = expectedXml.replaceAll("\\s+/>", "/>");
        expectedXml = expectedXml.replaceAll("\\s*alias=\"[^\"]+\"", "");
        Assert.assertFalse(xml.isEmpty());
        Assert.assertFalse(expectedXml.isEmpty());
        Assert.assertEquals(expectedXml, xml);
    }

    public static void assertSubmissionXmlWithEmptyManifestFile(String actualSubmissionXml) {
        String expected = "<SUBMISSION_SET>\n"
            + "  <SUBMISSION>\n"
            + "    <ACTIONS>\n"
            + "        <ACTION>\n"
            + "               <ADD />\n"
            + "        </ACTION>\n"
            + "    </ACTIONS>\n"
            + "    <SUBMISSION_ATTRIBUTES>\n"
            + "        <SUBMISSION_ATTRIBUTE>\n"
            + "            <TAG>ENA-SUBMISSION-TOOL</TAG>\n"
            + "            <VALUE>WebinCli</VALUE>\n"
            + "        </SUBMISSION_ATTRIBUTE>\n"
            + "        <SUBMISSION_ATTRIBUTE>\n"
            + "            <TAG>ENA-MANIFEST-FILE</TAG>\n"
            + "            <VALUE />\n"
            + "        </SUBMISSION_ATTRIBUTE>\n"
            + "        <SUBMISSION_ATTRIBUTE>\n"
            + "            <TAG>ENA-MANIFEST-FILE-MD5</TAG>\n"
            + "            <VALUE>d41d8cd98f00b204e9800998ecf8427e</VALUE>\n"
            + "        </SUBMISSION_ATTRIBUTE>\n"
            + "    </SUBMISSION_ATTRIBUTES>\n"
            + "  </SUBMISSION>\n"
            + "</SUBMISSION_SET>";

        XmlTester.assertXml(actualSubmissionXml, expected);
    }
}
