package extraction;

import org.apache.log4j.Logger;
import util.FileOperation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * 提取源码信息路径信息。
 * <p>
 * id_commitId_fileIds
 * 由所有id、commit_id和file_id构成的主键列表。
 * dictionary
 * 存放实际属性名称和属性代号名称对的map。key值为实际属性名称。
 * dictionary2
 * 存放属性代号名称和实际属性名称对的map。
 * currStrings
 * 当前出现过的属性。
 * bow
 * 用以提取源码信息路径信息的词向量类。
 * content
 * 实际得到的各实例，key为id_commitId_fileIds中的元素，value为对应的属性值。当key值为(-1,-1,-1)
 * 时对应的值为属性名称。
 * colMap
 * content中属性及其索引的map。因为在持续更新实例中数据的过程中，某个实例的某个属性值可能需要改变
 * 则可根据此map快速对应到content中该属性的值，然后将其修改。
 * headmap
 * content中的属性字段，即存放所有属性名称的map。
 *
 * @author niu
 */
public class ExtractionBow extends Extraction {
    private static Logger logger = Logger.getLogger(ExtractionBow.class);
    Map<String, String> dictionary;
    Set<String> currStrings;
    Map<List<Integer>, StringBuffer> contentMap;
    Map<String, Integer> colMap;
    Set<String> fileLevelWordVector;
    Set<String> commitLevelWordVector;
    Set<String> hunkLevelWordVector;
    public static int patchNumStartIndex = 4;
    public static List<String> hunkMetricsNames = Arrays.asList("NOCN", "NOL", "NOFC", "NOV", "NON",
            "NOLO", "NORO", "NOAR", "NORS", "NOCS", "NOB", "NOO", "NOIH", "NOA");
    public static List<List<String>> identifiers = Arrays.asList(Arrays.asList("if", "else if", "else"),
            Arrays.asList("for", "while"), Arrays.asList("."), Arrays.asList("byte", "short", "int", "long", "float", "double",
                    "boolean", "char"), Arrays.asList("null"), Arrays.asList("&&", "||", "!"),
            Arrays.asList(">=", ">", "<=", "<", "=="), Arrays.asList("+", "-", "*", "/"), Arrays.asList("return"), Arrays
                    .asList("case"), Arrays.asList("break"), Arrays.asList("new"), Arrays.asList("extends", "implements"),
            Arrays.asList("="));

    /**
     * 提取第三部分信息。
     *
     * @param database 需要连接的数据库
     * @param startId
     * @param endId
     * @throws Exception
     */
    public ExtractionBow(String database, int startId,
                         int endId) throws Exception {
        super(database, startId, endId);

        dictionary = new LinkedHashMap<>();
        currStrings = new HashSet<>();
        contentMap = new LinkedHashMap<>();
        colMap = new HashMap<>();
        contentMap.put(titleIndex, new StringBuffer());
        for (List<Integer> list : commit_file_patch_offset_part) {
            contentMap.put(list, new StringBuffer());
        }
        if (hunkMetricsNames.size() != identifiers.size()) {
            logger.error("The size of hunkMetricsNames is not same as the size of identifiers.");
            return;
        }
        commitLevelWordVector = new HashSet<>();
        fileLevelWordVector = new HashSet<>();
        hunkLevelWordVector = new HashSet<>();
        changeLogInfo();
        patchInfo();
        pathInfo();
    }

    /**
     * 在content中针对指定的commit_id，更新属性s的值。
     * 此函数主要针对pathinfo和changelog，因为path信息或者changelog信息只与commit_id有关，与file_id无关。
     * 竟然是根据StringBuffer拆分的,效率太差了,应该用LinkedHashMap啊!
     *
     * @param s        属性名称。如果当前属性集中已有该属性，则对文件中每个实例更新该属性的值， 否则，向属性集中添加该属性，并初始化各属性值。
     * @param tent     当前已有的信息。
     * @param commitId 需要更新的实例的commit_id。
     * @param value    需要更新的值。
     * @return 更新内容后的content。
     */
    public Map<List<Integer>, StringBuffer> writeInfo(String s,
                                                      Map<List<Integer>, StringBuffer> tent, int commitId, Integer value) {
        if (!currStrings.contains(s)) {
            // 如果当前属性集不包含该属性，则新建该属性。
            currStrings.add(s);
            String ColName = "s" + dictionary.size();
            dictionary.put(s, ColName);
            colMap.put(ColName, colMap.size());

            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == -1) {
                    tent.put(titleIndex, tent.get(titleIndex).append(ColName + ","));
                } else if (list.get(0) == commitId) {
                    tent.put(list, tent.get(list).append(value + ","));
                } else {
                    tent.put(list, tent.get(list).append(0 + ","));
                }
            }
        } else {
            // 根据真实属性名获取content中的简要属性名。然后根据简要属性名快速得到该属性对应的列号。
            String column = dictionary.get(s);
            int index = colMap.get(column);
            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == commitId) {
                    StringBuffer newbuffer = new StringBuffer();
                    String[] arrayStrings = tent.get(list).toString().split(",");
                    for (int i = 0; i < index; i++) {
                        newbuffer.append(arrayStrings[i] + ",");
                    }
                    int update = Integer.parseInt(arrayStrings[index]) + value;
                    newbuffer.append(update + ",");
                    for (int i = index + 1; i < arrayStrings.length; i++) {
                        newbuffer.append(arrayStrings[i] + ",");
                    }
                    tent.put(list, newbuffer);
                }
            }

        }
        return tent;
    }

    /**
     * 获取所有的changelog信息,并将其加入content。 注意：changelog信息只跟commit_id有关，与file_id无关。
     *
     * @throws SQLException
     * @throws IOException
     */
    public void changeLogInfo() throws SQLException, IOException {
        logger.info("Extract changLog info.");
        for (Integer commitId : commit_parts) {
            if (commitId != -1) {
                sql = "select message from scmlog where id=" + commitId;
                resultSet = stmt.executeQuery(sql);
                resultSet.next();
                String message = resultSet.getString(1);
                Map<String, Integer> bp = Bow.bow(message);
                for (String s : bp.keySet()) {
                    commitLevelWordVector.add(s);
                    contentMap = writeInfo(s, contentMap, commitId, bp.get(s));
                }
            }
        }

    }

    /**
     * 获取源码和源码中改动的代码信息。
     * 针对每个更改的(commit_id,file_id)对，其可能(如果某次更改类型为d，即删除了某个文件，那么就没有对应的文件)
     * 对应一个java文件。 同时其对应于一个patch。需要根据脚本语言提前获得所有这些更改了的文件，并通过数据库获得所有的patch信息
     * 然后使用此函数提取源码中的一些信息。
     *
     * @throws SQLException
     * @throws IOException
     */
    public void patchInfo() throws SQLException, IOException {
        logger.info("Extract source info.");
        for (List<Integer> list : commit_file_patch_offset_part) {
            String patchString = hunks_cache_part.get(list);
            StringBuffer forSourceParse = new StringBuffer();
            List<String> forMetricsParse = new ArrayList<>();
            String[] lines = patchString.split("\n");
            Map<String, Integer> hunkMetrics = new LinkedHashMap<>();
            int LAH = 0;
            int LDH = 0;
            for (String line : lines) {
                if (line.contains("@@ -")) {
                    line = line.substring(line.lastIndexOf("@@") + 2, line.length());
                    forSourceParse.append(line);
                    forMetricsParse.add(line);
                } else if (line.startsWith("+") || line.startsWith("-")) {
                    if (line.startsWith("+")) {
                        LAH++;
                    } else {
                        LDH++;
                    }
                    line = line.substring(1);
                    forSourceParse.append(line);
                    forMetricsParse.add(line);
                } else {
                    forSourceParse.append(line);
                    forMetricsParse.add(line);
                }
            }
            hunkMetrics.put("LAH", LAH);
            hunkMetrics.put("LDH", LDH);
            hunkMetrics.put("NOH", LAH + LDH);
            int[] values = dealWithHunkMetrics(hunkMetrics, forMetricsParse);
            for (int i = 0; i < hunkMetricsNames.size(); i++) {
                hunkMetrics.put(hunkMetricsNames.get(i), values[i]);
            }
            Map<String, Integer> patchMap = Bow.bowP(forSourceParse.toString());
            for (List<String> identifierGroup : identifiers) {  //hunk matric is retained when word vectors and hunk metric are repeated.
                for (String identifier : identifierGroup) {
                    if (patchMap.containsKey(identifier)){
                        patchMap.remove(identifier);
                    }
                }
            }
            for (String s : patchMap.keySet()) {
                contentMap = writeInfo(s, contentMap, list.get(0), list.get(1), list.get(2), list.get(3),
                        patchMap.get(s));
                hunkLevelWordVector.add(s);
            }
            for (String s : hunkMetrics.keySet()) {
                contentMap = writeInfo(s, contentMap, list.get(0), list.get(1), list.get(2), list.get(3),
                        hunkMetrics.get(s));
            }
        }
    }

    private int[] dealWithHunkMetrics(Map<String, Integer> hunkMetrics, List<String> lines) {
        String[] annotatedStartIdentifier = {"*", "/"};
        int[] values = new int[hunkMetricsNames.size()];
        for (String line : lines) {
            line = line.trim();
            boolean annotatedLine = false;
            for (String startIdentifier : annotatedStartIdentifier) {  // whether it is an annotated line.
                if (line.startsWith(startIdentifier)) {
                    annotatedLine = true;
                    break;
                }
            }
            if (annotatedLine) {
                continue;
            }
            for (int i = 0; i < identifiers.size(); i++) {
                for (int j = 0; j < identifiers.get(i).size(); j++) {
                    if (line.contains(identifiers.get(i).get(j))) {
                        values[i]++;
                        line = line.replace(identifiers.get(i).get(j), " ");
                    }
                }
            }
        }
        return values;
    }

    /**
     * 针对给定的commit_id,file_id对，将tent中s的值更新。
     * 需要注意的是，这样的搭配导致extraction3提取的数据最后一个是逗号，
     * 导致weka无法识别，这个问题在Merge类的merge123()方法中处理。
     *
     * @param s        需要更新的属性
     * @param tent     需要更新的包含实例的实例集。
     * @param commitId 需要更新的实例对应的commit_id。
     * @param fileId   需要更新的实例对应的file_id。
     * @param value    需要更新的值。
     * @return 新的实例集。
     */
    public Map<List<Integer>, StringBuffer> writeInfo(String s, Map<List<Integer>, StringBuffer> tent, int commitId,
                                                      int fileId, int patch_id, int offset, int value) {
        if (!currStrings.contains(s)) {
            currStrings.add(s);
            String ColName = "s" + dictionary.size();
            dictionary.put(s, ColName);
            colMap.put(ColName, colMap.size());

            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == -1) {
                    tent.get(titleIndex).append(ColName + ",");
                } else if (list.get(0) == commitId && list.get(1) == fileId && list.get(2) == patch_id && list.get(3) == offset) {
                    tent.put(list, tent.get(list).append(value + ","));
                } else {
                    tent.put(list, tent.get(list).append(0 + ","));
                }
            }
        } else {
            String column = dictionary.get(s);
            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == commitId && list.get(1) == fileId && list.get(2) == patch_id && list.get(3) == offset) {
                    int index = colMap.get(column);
                    StringBuffer newbuffer = new StringBuffer();
                    String[] aStrings = tent.get(list).toString().split(",");
                    for (int i = 0; i < index; i++) {
                        newbuffer.append(aStrings[i] + ",");
                    }
                    int newValue = Integer.parseInt(aStrings[index] + value);
                    newbuffer.append(newValue + ",");
                    for (int i = index + 1; i < aStrings.length; i++) {
                        newbuffer.append(aStrings[i] + ",");
                    }
                    tent.put(list, newbuffer);
                }
            }
        }
        return tent;
    }

    /**
     * 获取path中的信息。
     *
     * @throws SQLException
     * @throws IOException
     */
    public void pathInfo() throws SQLException, IOException {
        logger.info("Extract path info.");
        for (List<Integer> list : commit_file_parts) {
            sql = "select current_file_path from actions where commit_id="
                    + list.get(0) + " and file_id=" + list.get(1);
            resultSet = stmt.executeQuery(sql);
            if (!resultSet.next()) {
                continue;
            }
            String path = resultSet.getString(1);
            Map<String, Integer> pathName = Bow.bowPP(path);
            for (String s : pathName.keySet()) {
                fileLevelWordVector.add(s);
                contentMap = writeInfo(s, contentMap, list.get(0), list.get(1), // 两个函数可以整合为一个
                        pathName.get(s));
            }
        }
    }

    private Map<List<Integer>, StringBuffer> writeInfo(String s, Map<List<Integer>, StringBuffer> tent,
                                                       int commit_id, int file_id, int value) {
        if (!currStrings.contains(s)) {
            currStrings.add(s);
            String ColName = "s" + dictionary.size();
            dictionary.put(s, ColName);
            colMap.put(ColName, colMap.size());

            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == -1) {
                    tent.get(titleIndex).append(ColName + ",");
                } else if (list.get(0) == commit_id && list.get(1) == file_id) {
                    tent.put(list, tent.get(list).append(value + ","));
                } else {
                    tent.put(list, tent.get(list).append(0 + ","));
                }
            }
        } else {
            String column = dictionary.get(s);
            for (List<Integer> list : tent.keySet()) {
                if (list.get(0) == commit_id && list.get(1) == file_id) {
                    int index = colMap.get(column);
                    StringBuffer newbuffer = new StringBuffer();
                    String[] aStrings = tent.get(list).toString().split(",");
                    for (int i = 0; i < index; i++) {
                        newbuffer.append(aStrings[i] + ",");
                    }
                    int newValue = Integer.parseInt(aStrings[index] + value);
                    newbuffer.append(newValue + ",");
                    for (int i = index + 1; i < aStrings.length; i++) {
                        newbuffer.append(aStrings[i] + ",");
                    }
                    tent.put(list, newbuffer);
                }
            }
        }
        return tent;
    }


    /**
     * 获取文本解析后的字典.
     *
     * @return
     */
    public Map<String, String> getDictionary() {
        return dictionary;
    }

    @Override
    public Map<List<Integer>, StringBuffer> getContentMap() throws SQLException {

        for (List<Integer> key : contentMap.keySet()) {
            StringBuffer value = contentMap.get(key);
            if (value.charAt(value.length() - 1) == ',') {
                contentMap.put(key, value.deleteCharAt(value.length() - 1));
            }
        }
        return contentMap;
    }
}
