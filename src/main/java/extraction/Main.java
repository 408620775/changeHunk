package extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static String gitProjectsHome = "/home/niubinbin/test/";

    public static void main(String[] args) throws Exception {
        getTotalCsvFile("MyHadoop",5501,5800,true,"hadoop");
    }

    public static void getTotalCsvFile(String database, int s, int e, boolean createMetaTable, String gitProjectName)
            throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta(database, s, e);
        if (createMetaTable) {
            extractionMeta.getMetaTableData();
            extractionMeta.just_in_time(gitProjectsHome + gitProjectName);
        }
        System.out.println(extractionMeta.countRatio());
        ExtractionMetrics extractionMetrics = new ExtractionMetrics(database, s, e);
        extractionMetrics.extraFromTxt("metricFiles/" + database + "Metrics.txt");
        ExtractionBow extractionBow = new ExtractionBow(database, s, e);
        List<Map<List<Integer>, StringBuffer>> list = new ArrayList<>();
        list.add(extractionMeta.getContentMap());
        list.add(extractionMetrics.getContentMap());
        list.add(extractionBow.getContentMap());
        FileOperation.writeContentMap(Merge.mergeMap(list), "csv/" + database + ".csv");
    }
}