package org.query.util;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.query.entity.BugReport;
import org.query.property.KeywordProperty;
import org.query.search_engine.LuceneSearch;

import java.io.*;
import java.util.*;

/**
 * @author CAI
 * @date 2023/5/10
 **/
public class EvalDataProcess {
    private final KeywordProperty keywordProperty = KeywordProperty.getInstance();

    private List<String> keywords = new ArrayList<>();

    private List<String[]> bugCorpusFilePaths = new ArrayList<>();

    String project = "";

    String model_name = "";
//    String model_name = "dnn";

    String predict_file_path = "";

    String predict_res_file_path = "";

    private void readKeywordFile() {
        try {
            BufferedReader in = new BufferedReader(new FileReader(keywordProperty.keywordFilepath));

            String lineContent;
            Set<String> wordSet = new HashSet<>();
            while ((lineContent = in.readLine()) != null) {
                StringBuilder stringBuilder = new StringBuilder();
                wordSet.addAll(List.of(lineContent.split(" ")));
                for (String word: wordSet) {
                    stringBuilder.append(word).append(" ");
                }
                lineContent = stringBuilder.toString().trim();
                keywords.add(lineContent);
                wordSet.clear();
            }

            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    private void readKeywordFile() {
//        try {
//            BufferedReader in = new BufferedReader(new FileReader(keywordProperty.keywordFilepath));
//
//            StringBuilder stringBuilder = new StringBuilder();
//
//            String lineContent;
//            while ((lineContent = in.readLine()) != null) {
//                if (!lineContent.equals("")) {
//                    String[] contents = lineContent.split("\t");
//                    if (contents[2].equals("1")) {
//                        stringBuilder.append(contents[0]).append(" ");
//                    }
//                }
//                else {
//                    String keyword = stringBuilder.toString().trim();
//                    keywords.add(keyword);
//                    stringBuilder = new StringBuilder();
//                }
//            }
//
//            in.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private void readTestFile() {
        try {
            CsvReader reader = new CsvReader(keywordProperty.testFilepath);

            reader.readHeaders();

            while (reader.readRecord()) {
                String[] record = reader.getValues();
                String project = record[0];
                String bugId = record[1];

                bugCorpusFilePaths.add(new String[]{bugId, project});
            }

            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readFile() {

        try {
            CsvReader reader = new CsvReader("F:\\query-reformulation\\dataset\\" + project + "\\data\\expand.csv");

            reader.readHeaders();

            while (reader.readRecord()) {
                String[] record = reader.getValues();
                String bugId = record[0];

                if (record[1].equals(""))
                    continue;

                String[] text = record[1].split(" ");
                String[] label = record[2].split(" ");
                StringBuilder stringBuilder = new StringBuilder();

                for (int index = 0; index < text.length; index++) {
                    if (Objects.equals(label[index], "1")) {
                        stringBuilder.append(text[index]).append(" ");
                    }
                }

                String keyword = stringBuilder.toString().trim();

                bugCorpusFilePaths.add(new String[]{bugId, project});
                keywords.add(keyword);
            }

            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readRefinementKeywordFile() {
        try {
//            CsvReader reader = new CsvReader("E:\\query-reformulation\\dataset\\" + project + "\\data\\t.csv");
            CsvReader reader = new CsvReader("E:\\query-reformulation\\dataset\\" + project + "\\data\\high_quality_multi_sent.csv");
//            CsvReader reader = new CsvReader("E:\\query-reformulation\\dataset\\" + project + "\\data\\refinement_result_with_human_1.csv");

            reader.readHeaders();

            while (reader.readRecord()) {
                String[] record = reader.getValues();
                String bugId = record[0];

                if (record[3].equals(""))
                    continue;

                String keyword = record[3];
//                String[] keywordArr = record[3].split(" ");
//                List<String> keywordList = new ArrayList<>();
//                for (String k: keywordArr) {
//                    k = k.replaceAll("[^a-z]", "");
//                    if (!k.equals("") && !keywordList.contains(k)) {
//                        keywordList.add(k);
//                    }
//                }
//
//                keyword = String.join(" ", keywordList).trim();
//                System.out.println(keyword);

                bugCorpusFilePaths.add(new String[]{bugId, project});
                keywords.add(keyword);
            }

            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void searchForCluster() {
        assert keywords.size() == bugCorpusFilePaths.size();

        try {

            File resFile = new File("E:\\query-reformulation\\dataset\\" + project + "\\data\\cluster.csv");
//            File resFile = new File(keywordProperty.resFilepath);

            if (!resFile.exists()) {
                if (resFile.createNewFile()) {
                    System.out.println("Successfully create file " + resFile.getName());
                } else {
                    System.out.println("Fail to create file " + resFile.getName());
                }
            }

            FileWriter resFileWriter = new FileWriter(resFile);

            CsvWriter csvWriter = new CsvWriter(resFileWriter, ',');

            String[] header = new String[]{"Project", "BugId", "cluster", "Keyword"};
//            String[] header = new String[]{"Project", "BugId", "Effectiveness", "AP", "Keyword"};
            csvWriter.writeRecord(header);

            for (int i = 0; i < bugCorpusFilePaths.size(); i++) {
                String project = bugCorpusFilePaths.get(i)[1];
                int bugId = Integer.parseInt(bugCorpusFilePaths.get(i)[0].replaceAll("[^0-9]", ""));

                String bugReportFile = keywordProperty.dict.get(project) + "//BugCorpus//" + String.valueOf(bugId) + ".txt";
                String luceneIndexPath = keywordProperty.dict.get(project) + "//lucene_index";

                BugReport bugReport = new BugReport(bugReportFile, bugId, project);

                LuceneSearch luceneSearch = new LuceneSearch(bugReport, false, luceneIndexPath);

                String keyword = keywords.get(i);
                keyword = keyword.replaceAll("[^a-zA-Z\s0-9]", "").trim();
//                System.out.println(keyword);

                List<String[]> searchRes = luceneSearch.searchCode(keyword);
                int codeId = luceneSearch.getFirstHitId(searchRes);
//                float[] metrics = luceneSearch.computeAll(searchRes);
//                float[] metrics = luceneSearch.computeAllMetrics(searchRes);

                String[] record = new String[]{project, String.valueOf(bugId), String.valueOf(codeId), keyword};
                csvWriter.writeRecord(record);
            }

            csvWriter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void readPredictResult() {
        try {
//            CsvReader csvReader = new CsvReader("E:\\query-reformulation\\processed_dataset\\" + project + "\\GA_total_result_0.csv");
//            csvReader.readHeaders();
//
//            List<String> low_quality_bug_id = new ArrayList<>();
//
//            while (csvReader.readRecord()) {
//                String[] record = csvReader.getValues();
//                String bugId = record[0];
//                low_quality_bug_id.add(bugId);
//            }

//            CsvReader reader = new CsvReader("E:\\query-reformulation\\processed_dataset\\" + project + "\\title_keywords.csv");
//            CsvReader reader = new CsvReader("E:\\query-reformulation\\processed_dataset\\" + project + "\\test_rank_keywords.csv");
//            CsvReader reader = new CsvReader("E:\\query-reformulation\\processed_dataset\\cross_project\\" + project + "\\predict_result_" + model_name + ".csv");
//            CsvReader reader = new CsvReader("F:\\query-reformulation\\rerun_data\\" + project + "\\predict_result_" + model_name + ".csv");
//            CsvReader reader = new CsvReader("Z:\\query-reformulation\\processed_dataset\\" + project + "\\predict_result_" + model_name + ".csv");
//            CsvReader reader = new CsvReader("Z:\\query-reformulation\\processed_dataset\\" + project + "\\new_refinement_result.csv");

            CsvReader reader = new CsvReader(predict_file_path);
            reader.setSafetySwitch(false);
//            CsvReader reader = new CsvReader("E:\\query-reformulation\\processed_dataset\\" + project + "\\predict_result_rf.csv");
//
            reader.readHeaders();

            while (reader.readRecord()) {
                String[] record = reader.getValues();
                String bugId = record[0];
//                if (!low_quality_bug_id.contains(bugId))
//                    continue;

                if (record[2].equals("")) {
                    continue;
                }

                String keyword = record[2];
                bugCorpusFilePaths.add(new String[]{bugId, project});
                keywords.add(keyword);
            }

            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void search() {
        assert keywords.size() == bugCorpusFilePaths.size();

        try {

//            File resFile = new File("E:\\query-reformulation\\dataset\\" + project + "\\data\\GA_metrics.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\" + project + "\\tr_predict_result_80_20.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\" + project + "\\posi_pro_tf_sp\\random_tree\\predict_result_drop_dup_refine_history_blizzard_80_20.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\" + project + "\\title_result.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\" + project + "\\text_rank_result.csv");
//            File resFile = new File("Z:\\query-reformulation\\processed_dataset\\" + project + "\\new_refinement_rank_result.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\cross_project\\" + project + "\\predict_rank_result_" + model_name + ".csv");
//            File resFile = new File("F:\\query-reformulation\\rerun_data\\" + project + "\\predict_rank_result_" + model_name + ".csv");
//            File resFile = new File("Z:\\query-reformulation\\processed_dataset\\" + project + "\\predict_rank_result_" + model_name + ".csv");
//            File resFile = new File("F:\\query-reformulation\\dataset\\" + project + "\\data\\expand_result.csv");
//            File resFile = new File("E:\\query-reformulation\\processed_dataset\\" + project + "\\predict_rank_result_rf.csv");
//            File resFile = new File(keywordProperty.resFilepath);

            File resFile = new File(predict_res_file_path);

            if (!resFile.exists()) {
                if (resFile.createNewFile()) {
                    System.out.println("Successfully create file " + resFile.getName());
                } else {
                    System.out.println("Fail to create file " + resFile.getName());
                }
            }

            FileWriter resFileWriter = new FileWriter(resFile);

            CsvWriter csvWriter = new CsvWriter(resFileWriter, ',');

            String[] header = new String[]{"Project", "BugId", "Effectiveness", "AP", "RR", "Keyword"};
//            String[] header = new String[]{"Project", "BugId", "Effectiveness", "AP", "Keyword"};
            csvWriter.writeRecord(header);

            for (int i = 0; i < bugCorpusFilePaths.size(); i++) {
                String project = bugCorpusFilePaths.get(i)[1];
                int bugId = Integer.parseInt(bugCorpusFilePaths.get(i)[0].replaceAll("[^0-9]", ""));

                String bugReportFile = keywordProperty.dict.get(project) + "//BugCorpus//" + bugId + ".txt";
//                String luceneIndexPath = keywordProperty.dict.get(project) + "//lucene_index_bm25";
                String luceneIndexPath = keywordProperty.dict.get(project) + "//lucene_index";

                BugReport bugReport = new BugReport(bugReportFile, bugId, project);

                LuceneSearch luceneSearch = new LuceneSearch(bugReport, false, luceneIndexPath);

                String keyword = keywords.get(i);
                keyword = keyword.replaceAll("[^a-zA-Z\s0-9]", "").trim();
//                System.out.println(keyword);

                List<String[]> searchRes = luceneSearch.searchCode(keyword);
                float[] metrics = luceneSearch.computeAll(searchRes);
//                float[] metrics = luceneSearch.computeAllMetrics(searchRes);

//                String[] record = new String[]{project, String.valueOf(bugId), String.valueOf(metrics[0]), String.valueOf(metrics[1]), keyword};
                String[] record = new String[]{project, String.valueOf(bugId), String.valueOf(metrics[0]), String.valueOf(metrics[1]), String.valueOf(metrics[2]), keyword};
                csvWriter.writeRecord(record);
            }

            csvWriter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void evalData() {

//        readTestFile();
//        readKeywordFile();
//        readFile();
//        readRefinementKeywordFile();
        readPredictResult();
        search();

//        readRefinementKeywordFile();
//        searchForCluster();

    }

    public static void main(String[] args) {
//        KeywordProperty.createInstance("F:\\query-reformulation\\Keyword-T5\\eval_data\\pagerank.txt", true, false, false);
//        KeywordProperty.createInstance("F:\\query-reformulation\\Keyword-T5\\eval_data\\res_drop_prompt.txt", false, false, true);
//        KeywordProperty.createInstance("F:\\query-reformulation\\Keyword-T5\\eval_data\\res_drop.txt", false, true, false);
//        KeywordProperty.createInstance("F:\\query-reformulation\\keyword_extraction\\output\\bert_bilstm_crf\\token_labels_test.txt", false, false, false);
        KeywordProperty.createInstance("F:\\query-reformulation\\Keyword-T5\\eval_data\\res_refine.txt", false, false, false);

//        String[] projects = new String[]{"AspectJ"};
//        String[] projects = new String[]{"AspectJ", "Birt", "Eclipse_Platform_UI", "JDT", "SWT", "Tomcat"};

//        for (String project: projects) {
//            EvalDataProcess eval = new EvalDataProcess();
//
//            eval.project = project;
//
//            eval.evalData();
//            System.out.println(eval.project);
//        }

        EvalDataProcess eval = new EvalDataProcess();

        try {
            if (args != null && args.length != 0) {
                eval.project = args[0];
                eval.model_name = args[1];
                eval.predict_file_path = args[2];
                eval.predict_res_file_path = args[3];
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println(e.toString());
            return;
        }


        eval.evalData();
        System.out.println(eval.project);

    }
}
