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

package uk.ac.ebi.ena.study;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import uk.ac.ebi.ena.webin.cli.WebinCli;
import uk.ac.ebi.ena.webin.cli.WebinCliException;

public class Study {
	private  List<String> locusTagsList = new ArrayList<>();
    private String projectId;
    private final static String VALIDATION_ERROR_STUDY = "Unknown study (project) or the study cannot be referenced by your submission account. " +
            "Studies must be submitted before they can be referenced in the submission. Study: ";
    private final static String SYSTEM_ERROR_INTERNAL = "An internal server error occurred when retrieving study information. ";
    private final static String SYSTEM_ERROR_UNAVAILABLE = "A service unavailable error occurred when retrieving study information. ";
    private final static String SYSTEM_ERROR_OTHER = "A server error occurred when retrieving study information. ";

    public static Study 
    getStudy( String studyId, String userName, String password, boolean TEST )
    {
        try {
            Study study = new Study();
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet( ( TEST ? "https://wwwdev.ebi.ac.uk/ena/submit/drop-box/reference/project/" : "https://www.ebi.ac.uk/ena/submit/drop-box/reference/project/" ) +  URLEncoder.encode( studyId.trim(), "UTF-8" ) );
            String encoding = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes());
            httpGet.setHeader("Authorization", "Basic " + encoding);
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int responsecode = response.getStatusLine().getStatusCode();
            switch (responsecode) {
                case HttpStatus.SC_OK:
                    List<String> resultsList = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
                    String result = resultsList.stream()
                            .collect(Collectors.joining(" "));
                    study.extractResults( result, studyId );
                    return study;
                case HttpStatus.SC_BAD_REQUEST:
                case HttpStatus.SC_NOT_FOUND:
                    throw WebinCliException.createValidationError(VALIDATION_ERROR_STUDY, studyId);
                case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                    throw WebinCliException.createSystemError(SYSTEM_ERROR_INTERNAL);
                case HttpStatus.SC_SERVICE_UNAVAILABLE:
                    throw WebinCliException.createSystemError(SYSTEM_ERROR_UNAVAILABLE);
                case HttpStatus.SC_UNAUTHORIZED:
                    throw WebinCliException.createUserError(WebinCli.AUTHENTICATION_ERROR);
                default:
                    throw WebinCliException.createSystemError(SYSTEM_ERROR_OTHER);
            }
        } catch( IOException e ) 
        {
            throw WebinCliException.createSystemError(SYSTEM_ERROR_OTHER);
        }
    }

    public List<String> getLocusTagsList() {
        return locusTagsList;
    }
    
    public String getProjectId()
    {
    	return projectId;
    }

    public void setLocusTagsList(List<String> locusTagsList) {
		this.locusTagsList = locusTagsList;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

    private void extractResults(String result, String studyId) {
        try {
            JSONParser jsonParser = new JSONParser();
            StringReader reader = new StringReader(result);
            JSONObject jsonObject = (JSONObject) jsonParser.parse(reader);
            boolean canBeReferenced = (boolean)jsonObject.get("canBeReferenced");
            if (!canBeReferenced)
                throw WebinCliException.createUserError(VALIDATION_ERROR_STUDY, studyId);
            JSONArray jsonArray = (JSONArray)jsonObject.get("locusTags");
            projectId = (String) jsonObject.get("bioProjectId");
            if (jsonArray != null && !jsonArray.isEmpty())
                jsonArray.forEach(p -> locusTagsList.add( p.toString()));
        } catch (IOException | ParseException e) {
            throw WebinCliException.createSystemError(SYSTEM_ERROR_OTHER, e.getMessage());
        }
    }
}
