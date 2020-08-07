/*
 * Copyright 2018-2019 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.ena.webin.cli.xml;

import java.nio.file.Path;

import org.jdom2.Element;

import uk.ac.ebi.ena.webin.cli.utils.FileUtils;

public class XmlWriterHelper {

  public static Element createTextElement(String name, String text) {
    Element e = new Element(name);
    e.setText(text);
    return e;
  }

  private static Element createFileElement(
      String fileName, String fileType, String digest, String checksum) {
    Element e = new Element("FILE");
    e.setAttribute("filename", fileName);
    e.setAttribute("filetype", String.valueOf(fileType));
    e.setAttribute("checksum_method", digest);
    e.setAttribute("checksum", checksum);
    return e;
  }

  public static Element createFileElement(
      Path inputDir, Path uploadDir, Path file, String fileType) {
    String path =
        file.startsWith(inputDir) ? inputDir.relativize(file).toString() : file.toFile().getName();

    return createFileElement(
        String.valueOf(uploadDir.resolve(path)).replaceAll("\\\\+", "/"),
        String.valueOf(fileType),
        "MD5",
        FileUtils.calculateDigest("MD5", file.toFile()));
  }
}
