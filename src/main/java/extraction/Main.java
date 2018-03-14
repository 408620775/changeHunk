package extraction;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    private static Logger logger = Logger.getLogger(Main.class);
    public static String gitProjectsHome = "/home/niubinbin/test/";
    public static String[][] projectsInfo = {{"MyAnt", "1001", "1500", "ant"},
            {"MyBuck", "1001", "1300", "buck"},
            {"MyLucene", "1001", "1500", "lucene"},
            {"MyTomcat", "1001", "1500", "tomcat"},
            {"MyJedit", "1001", "1500", "jEdit"},
            {"MySynapse", "1001", "1300", "synapse"},
            {"MyVoldemort", "501", "800", "voldemort"},
            {"MyItextpdf", "501", "800", "itextpdf"},
            {"MyFlink", "1001", "1300", "flink"},
            {"MyHadoop", "5501", "5800", "hadoop"}};

    public static void main(String[] args) throws Exception {
        for (int i = 1; i < projectsInfo.length; i++) {
            getTotalCsvFile(projectsInfo[i][0], Integer.parseInt(projectsInfo[i][1]), Integer.parseInt
                    (projectsInfo[i][2]), false, projectsInfo[i][3]);
        }

    }

    public static void getTotalCsvFile(String database, int s, int e, boolean createMetaTable, String gitProjectName)
            throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta(database, s, e);
        if (createMetaTable) {
            extractionMeta.getMetaTableData(gitProjectsHome + gitProjectName);
        }
        logger.info("Bug Ratio: "+extractionMeta.countRatio());
        ExtractionMetrics extractionMetrics = new ExtractionMetrics(database, s, e);
        extractionMetrics.extraFromTxt("metricFiles/" + database + "Metrics.txt");
        ExtractionBow extractionBow = new ExtractionBow(database, s, e);
        List<Map<List<Integer>, StringBuffer>> list = new ArrayList<>();
        list.add(extractionMeta.getContentMap());
        list.add(extractionMetrics.getContentMap());
        list.add(extractionBow.getContentMap());
        FileOperation.writeContentMap(Merge.mergeMap(list), "csv/" + database + ".csv");
        extractionMeta = null;
        extractionMetrics = null;
        extractionBow = null;
    }
}