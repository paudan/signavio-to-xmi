/*
 * Copyright 2020 Paulius Danenas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ktu.isd.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import joinery.DataFrame;
import joinery.DataFrame.JoinType;
import joinery.DataFrame.Predicate;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.json.JSONObject;


public class ProcessFilter {

    private Path model_dir;

    public ProcessFilter(Path model_dir) {
        this.model_dir = model_dir;
    }

    public String[] parseMetadata(File path) {
        try (InputStream is = new FileInputStream(path)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
            JSONObject obj = new JSONObject(sb.toString());
            JSONObject modelObj = obj.getJSONObject("model");
            if (modelObj == null)  return null;
            String lang = modelObj.optString("naturalLanguage");
            String modelLang = modelObj.optString("modelingLanguage");
            return new String[]{path.getPath(), lang, modelLang};
        } catch (FileNotFoundException e) {
            Logger.getLogger(ProcessFilter.class.getName()).log(Level.SEVERE, null, e);
        } catch (IOException | JSONException e) {
            Logger.getLogger(ProcessFilter.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }

    public DataFrame metadataDataFrame() {
        File[] metadataFiles = model_dir.toFile().listFiles((File dir, String name) -> name.contains(".meta.json"));
        List<String> names = new ArrayList<>();
        List<String> modelNames = new ArrayList<>();
        List<String> lang = new ArrayList<>();
        List<String> modelLang = new ArrayList<>();
        for (int i = 0; i < metadataFiles.length; i++) {
            String[] results = parseMetadata(metadataFiles[i]);
            if (results == null)
                continue;
            String fileName = Paths.get(results[0]).getFileName().toString();
            names.add(fileName);
            modelNames.add(fileName.replace(".meta", ""));
            lang.add(results[1]);
            modelLang.add(results[2]);
        }
        return new DataFrame(Collections.emptyList(), 
                Arrays.asList("filename.meta", "filename", "language", "modelLanguage"),
                Arrays.asList(names, modelNames, lang, modelLang));
    }

    public DataFrame generateIndex(String outputFile) {
        File[] modelFiles = model_dir.toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return FilenameUtils.getExtension(name).equalsIgnoreCase("json") && !name.contains(".meta.json");
            }

        });
        File[] metadataFiles = model_dir.toFile().listFiles((File dir, String name) -> name.contains(".meta.json"));
        DataFrame dfModels = new DataFrame();
        List<String> modelFileNames = Arrays.asList(modelFiles).stream().map(File::toString).collect(Collectors.toList());
        dfModels.add("filename", modelFileNames);
        DataFrame joined = dfModels.join(metadataDataFrame(), JoinType.INNER)
                .drop("filename_left")
                .rename(Collections.singletonMap("filename_right", "filename"));
        if (outputFile != null)
        try {
            joined.writeCsv(outputFile);
        } catch (IOException ex) {
            Logger.getLogger(ProcessFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return joined;
    }
    
    public List<String> filterFilenames(DataFrame index, Path basePath, String prefix) {
        DataFrame filtered = index.select(new Predicate<Object>() {
            @Override
            public Boolean apply(List<Object> value) {
                return String.class.cast(value.get(2)).equalsIgnoreCase("en") && String.class.cast(value.get(3)).startsWith(prefix); 
            }
            
        });
        Stream<String> fnames = filtered.col("filename").stream()
                .map(x -> Paths.get(basePath.toString(), String.class.cast(x)).toString());
        System.out.println(filtered.length());
        return fnames.collect(Collectors.toList());
    }

    public static void main(String... args) throws IOException {
        Path basePath = Paths.get("/mnt/DATA/Darbas/KTU/code/bpmai/models");
        String indexFile = "bpmai_index.csv";
        ProcessFilter filter = new ProcessFilter(basePath);
        // filter.generateIndex(indexFile);
        DataFrame index = DataFrame.readCsv(indexFile);
        filter.filterFilenames(index, basePath, "bpmn20");
        filter.filterFilenames(index, basePath, "UMLUseCase");
        filter.filterFilenames(index, basePath, "UML22Class");
    }

}
