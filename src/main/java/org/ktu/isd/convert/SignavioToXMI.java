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
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import joinery.DataFrame;
import org.activiti.cycle.RepositoryException;
import org.activiti.cycle.impl.connector.signavio.SignavioConnector;
import org.apache.commons.io.FilenameUtils;

public class SignavioToXMI {

    private Path models_dir;
    private Path output_dir;
    private SignavioConnector conn;

    public SignavioToXMI(Path models_dir, Path output_dir) {
        this.models_dir = models_dir;
        this.output_dir = output_dir;
        if (!Files.exists(output_dir) || !Files.isDirectory(output_dir))
            try {
                Files.createDirectory(output_dir);
            } catch (IOException ex) {
                Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
            }
        this.conn = new SignavioConnector();
    }

    public void iterateDir(Path srcPath, Path targetPath) throws IOException {
        File[] modelFiles = srcPath.toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return FilenameUtils.getExtension(name).equalsIgnoreCase("json") && !name.contains("metadata.json");
            }

        });
        for (File modelFile : modelFiles)
            transform(modelFile, targetPath);
        File[] subdirs = srcPath.toFile().listFiles(File::isDirectory);
        for (File subdir : subdirs) {
            Path targetDir = Paths.get(targetPath.toString(), srcPath.getFileName().toString());
            System.out.println(targetDir);
            if (!Files.exists(targetDir))
                Files.createDirectory(targetDir);
            iterateDir(subdir.toPath(), targetDir);
        }
    }

    public void iterate() throws IOException {
        File[] directories = models_dir.toFile().listFiles(File::isDirectory);
        for (File dir : directories) {
            Path targetDir = Paths.get(output_dir.toString(), dir.getName());
            if (!Files.exists(targetDir))
                iterateDir(dir.toPath(), output_dir);
        }
        // Remove empty directories
        Files.walkFileTree(output_dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.toFile().list().length == 0)
                    Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    public void iterateSimple(String indexFile) throws IOException {
        ProcessFilter filter = new ProcessFilter(models_dir);
        DataFrame index = indexFile != null ? DataFrame.readCsv(indexFile) : filter.generateIndex(null);
        List<String> modelFiles = filter.filterFilenames(index, models_dir, "bpmn20");
        for (String file: modelFiles) {
            System.out.println(file);
            File f = new File(file);
            transform(f, output_dir);
            Files.copy(Paths.get(file.replace(".json", ".svg")), Paths.get(output_dir.toString(), f.getName().replace(".json", ".svg")));
        }
    }

    public void transform(File inputFile, Path outputDir) {
        StringBuilder sb = null;
        String outputName = FilenameUtils.getBaseName(inputFile.getName()) + ".bpmn";
        Path outputFile = Paths.get(outputDir.toString(), outputName);
        // Skip if such file exists (could have been generated before)
        if (Files.exists(outputFile))
            return;
        String xmi = null;
        try (InputStream is = new FileInputStream(inputFile)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null)
                sb.append(line + "\n");
            xmi = this.conn.transformJsonToBpmn20Xml(sb.toString());
        } catch (IOException ex) {
            Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RepositoryException ex) {
            Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
            xmi = null;
        }
        if (xmi == null || xmi.trim().length() == 0)
            return;
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            writer.write(xmi);
            Files.copy(Paths.get(inputFile.toString().replace(".json", ".svg")), Paths.get(outputFile.toString().replace(".bpmn", ".svg")));
        } catch (IOException ex) {
            Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String... args) {
        String base_path = "/mnt/DATA/Darbas/KTU/code";
        Path outputDir = Paths.get(base_path, "modelCollection_transformed");
        try {
            SignavioToXMI converter = new SignavioToXMI(Paths.get(base_path, "modelCollection_1559160740359"), outputDir);
            converter.iterate();
        } catch (IOException ex) {
            Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
        }

//        String basePath = "/mnt/DATA/Darbas/KTU/code";
//        try {
//            SignavioToXMI converter = new SignavioToXMI(Paths.get(basePath, "bpmai", "models"), Paths.get(basePath, "dataset", "bpmai"));
//            converter.iterateSimple("bpmai_index.csv");
//        } catch (IOException ex) {
//            Logger.getLogger(SignavioToXMI.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

}
