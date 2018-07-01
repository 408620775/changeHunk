package extraction;

import org.apache.log4j.Logger;
import util.FileOperation;
import util.PropertyUtil;

import java.io.*;
import java.util.*;

public class Main {
    private static Logger logger = Logger.getLogger(Main.class);
    public static String gitProjectsHome = "/home/niubinbin/test/";


    public static void main(String[] args) throws Exception {
        String[] projectInfo = PropertyUtil.projectsInfo[6];
        int[] result = testSourceOfWordForProject(projectInfo[0],Integer.parseInt(projectInfo[1]),Integer.parseInt(projectInfo[2]));
        for (int i : result) {
            System.out.print(i+"  ");
        }
        //ExtractionMeta extractionMeta = new ExtractionMeta("MyVoldemort",501,800);
        //System.out.println(Extraction.commit_parts.get(0));
        //System.out.println(Extraction.commit_parts.get(Extraction.commit_parts.size()-1));
//        for (int i = 0; i < projectsInfo.length; i++) {
//            ExtractionMeta extractionMeta = new ExtractionMeta(projectsInfo[i][0], Integer.parseInt(projectsInfo[i][1]),
//                    Integer.parseInt(projectsInfo[i][2]));
//            extractionMeta.getLOCFileForClassification("LOCFiles/"+projectsInfo[i][0]+"LOC");
//        }
//        for (int i = 0; i < PropertyUtil.projectsInfo.length; i++) {
//            getTotalCsvFile(PropertyUtil.projectsInfo[i][0], Integer.parseInt(PropertyUtil.projectsInfo[i][1]), Integer.parseInt
//                    (PropertyUtil.projectsInfo[i][2]), false, PropertyUtil.projectsInfo[i][3]);
//        }
        //MyTool.countAttributeShare(3,"arffs","dicts");
    }

    public static void testSourceOfWord() throws Exception {
        for (String[] projectInfo : PropertyUtil.projectsInfo) {
            testSourceOfWordForProject(projectInfo[0],Integer.parseInt(projectInfo[1]),Integer.parseInt(projectInfo[2]));
        }
    }

    private static int[] testSourceOfWordForProject(String projectName, int startId, int endId) throws Exception {
        BufferedReader bReader = new BufferedReader(new FileReader(new File("./arffs/"+projectName+".arff")));
        String line = "";
        while ((line=bReader.readLine())!=null){
            if (line.startsWith("@attribute")){
                break;
            }
        }
        Set<String> formatAttributeNameSet = new HashSet<>();
        while (line.startsWith("@attribute")){
            String attributeName = line.split(" ")[1];
            if (attributeName.startsWith("s")&&Character.isDigit(attributeName.charAt(1))){
                formatAttributeNameSet.add(attributeName);
            }
            line = bReader.readLine();
        }
        bReader.close();
        bReader = new BufferedReader(new FileReader(new File("./dicts/"+projectName+"Dict")));
        Map<String,String> formatNameToActrulName = new HashMap<>();
        while ((line=bReader.readLine())!=null){
            if (!line.equals("")){
                String[] array = line.split("\\s+");
                formatNameToActrulName.put(array[1],array[0]);
            }
        }
        Set<String> actrulAttributeNameSet = new HashSet<>();
        for (String formatAttributeName : formatAttributeNameSet) {
            if (!formatNameToActrulName.containsKey(formatAttributeName)){
                logger.error(projectName+": Dict don't contain the attribute: "+formatAttributeName);
            }
            actrulAttributeNameSet.add(formatNameToActrulName.get(formatAttributeName));
        }

        ExtractionBow extractionBow = new ExtractionBow(projectName,startId,endId);
        int[] result = new int[3];
        for (String name : actrulAttributeNameSet) {
            String level = "";
            if (extractionBow.commitLevelWordVector.contains(name)){
                result[0]++;
                level+="C";
            }
            if (extractionBow.fileLevelWordVector.contains(name)){
                result[1]++;
                level+="F";
            }
            if (extractionBow.hunkLevelWordVector.contains(name)){
                result[2]++;
                level+="H";
            }
            System.out.println(name+" level : "+level);
        }
        return result;
    }

    public static void getTotalCsvFile(String database, int s, int e, boolean createMetaTable, String gitProjectName)
            throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta(database, s, e);
        if (createMetaTable) {
            extractionMeta.getMetaTableData(gitProjectsHome + gitProjectName);
        }
        logger.info("Bug Ratio: " + extractionMeta.countRatio());
        ExtractionMetrics extractionMetrics = new ExtractionMetrics(database, s, e);
        extractionMetrics.extraFromTxt("metricFiles/" + database + "Metrics.txt");
        ExtractionBow extractionBow = new ExtractionBow(database, s, e);
        FileOperation.writeDict("dicts/"+database+"Dict",extractionBow.dictionary);
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