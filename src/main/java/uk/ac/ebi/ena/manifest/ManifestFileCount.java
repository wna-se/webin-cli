/*
 * Copyright 2018 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package uk.ac.ebi.ena.manifest;

public class ManifestFileCount {
    private final String fileType;
    private final Integer minCount;
    private final Integer maxCount;

    public ManifestFileCount(String fileType, Integer minCount, Integer maxCount) {
        this.fileType = fileType;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    public String getFileType() {
        return fileType;
    }
    public Integer getMinCount() {
        return minCount;
    }
    public Integer getMaxCount() {
        return maxCount;
    }
}