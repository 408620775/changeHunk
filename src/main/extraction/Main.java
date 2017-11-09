package src.main.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args){

    }

    private static void checkConsistency(Map<List<Integer>, StringBuffer> map1) {
        List<Integer> title = new ArrayList<>();
        title.add(-1);
        title.add(-1);
        int titleSize = map1.get(title).toString().split(",").length;
        for (List<Integer> key : map1.keySet()) {
            if (map1.get(key).toString().split(",").length != titleSize) {
                System.out.println("Error! " + key.get(0) + " " + key.get(1));
            }
        }
    }

    static public void Automatic1(String project, int start_commit_id,
                                  int end_commit_id) throws Exception {
        String database = "My"
                + project.toLowerCase().substring(0, 1).toUpperCase()
                + project.toLowerCase().substring(1);
        ExtractionMeta extractionMeta = new ExtractionMeta(database, start_commit_id,
                end_commit_id);
        // ExtractionMetrics extraction2 = new ExtractionMetrics(database, start_commit_id,
        // end_commit_id);
        // extraction2.Get_icfId();
        // Process process = Runtime.getRuntime().exec(
        // "./home/niu/workspace/changeClassify/src/extraction/GetFile.sh "
        // + project);
        // System.out.println("the exit value of process is "
        // + process.exitValue());
    }

    static public void Automatic2(String project, int start_commit_id,
                                  int end_commit_id) throws Exception {
        String database = "My"
                + project.toLowerCase().substring(0, 1).toUpperCase()
                + project.toLowerCase().substring(1);
        ExtractionMeta extractionMeta = new ExtractionMeta(database, start_commit_id,
                end_commit_id);
        ExtractionMetrics extractionMetrics = new ExtractionMetrics(database, start_commit_id,
                end_commit_id);
        String metric = database + "Metrics.txt";
        extractionMetrics.extraFromTxt(metric);
        ExtractionBow extractionBow = new ExtractionBow(database, project + "Files",
                start_commit_id, end_commit_id);

        List<List<Integer>> commit_fileIds = extractionMeta.commit_fileIds;
        List<Map<List<Integer>, StringBuffer>> list = new ArrayList<>();
        Map<List<Integer>, StringBuffer> map1 = extractionMeta
                .getContentMap(commit_fileIds);
        checkConsistency(map1);
        Map<List<Integer>, StringBuffer> map2 = extractionMetrics
                .getContentMap(commit_fileIds);
        checkConsistency(map2);
        Map<List<Integer>, StringBuffer> map3 = extractionBow
                .getContentMap(commit_fileIds);
        checkConsistency(map3);
        list.add(map1);
        list.add(map2);
        list.add(map3);
        FileOperation.writeContentMap(Merge.mergeMap(list), database + ".csv");
    }
}
