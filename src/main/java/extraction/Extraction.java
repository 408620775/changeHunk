package extraction;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * 提取数据的超类。
 * <p>
 * 将commit按照时间排序，提取指定范围内所有commit的若干信息。若干信息的提取分别
 * 由三个子类去实现。需要注意的是，由于miningit分配给各commit的id并不是其实际提交的
 * 顺序（由于多线程并发导致），所以对于commit的排序不应根据其id排序，而应根据 commit_date排序。
 *
 * @author niu
 * @see ResultSet
 * @see SQLException
 * @see Statement
 */
public abstract class Extraction {
    private static Logger logger = Logger.getLogger(ExtractionMeta.class);
    public static int start;
    public static int end;
    public static String sql;
    public static String databaseName;
    public static String metaTableName = "metaHunk";
    public static String metaTableNamekey = "MetaTableName";
    public static String hunksCacheKey = "cacheHunks";
    public static String stringPropertyYes = "Yes";
    public static boolean hasLoadProperty = false;
    public static boolean boolCacheHunk = true;
    public static Statement stmt;
    public static ResultSet resultSet;
    public static SQLConnection sqlL;
    public static List<Integer> commit_ids;
    public static List<Integer> commitIdPart;
    public static List<Integer> title = Arrays.asList(-1, -1, -1, -1);
    public static List<List<Integer>> commit_fileIds;
    public static List<List<Integer>> commit_file_patch_offset;
    public static Map<List<Integer>, String> hunksCache;
    public static String databasePropertyPath = "src/main/resources/database.properties";

    public Extraction(String database, int start, int end) throws IOException, SQLException {
        this.start = start;
        this.end = end;
        this.databaseName = database;
        this.sqlL = SQLConnection.getConnection(database, databasePropertyPath);
        this.stmt = sqlL.getStmt();
        initialKeys();
        if (!hasLoadProperty) {
            loadProperty(databasePropertyPath);
        }
    }

    /**
     * 加载配置文件中的相关设置,用于1.定义mataTable名称.
     *
     * @param propertyFilePath
     * @throws IOException
     */
    public void loadProperty(String propertyFilePath) throws IOException, SQLException {
        Properties properties = new Properties();
        File propertyFile = new File(propertyFilePath);
        FileReader fReader = new FileReader(propertyFile);
        properties.load(fReader);
        if (properties.containsKey(metaTableNamekey)) {
            metaTableName = properties.getProperty(metaTableNamekey);
        }
        logger.info("load database properties success!");
    }

    /**
     * 根据给定的范围获取有效的commit_file对,也就是包括删除在内的记录对.
     *
     * @throws Exception
     */
    private void initialKeys() throws IllegalArgumentException, SQLException, IOException {
        logger.info("Initial keys!");
        commit_ids = new ArrayList<>();
        commitIdPart = new ArrayList<>();
        commit_fileIds = new ArrayList<>();
        commit_file_patch_offset = new ArrayList<>();
        hunksCache = new LinkedHashMap<>();
        sql = "select id from scmlog order by commit_date";
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            commit_ids.add(resultSet.getInt(1));
        }
        if (start < 0) {
            logger.error("start commit_id can't be less 0!");
            throw new IllegalArgumentException("start commit_id can't be less 0!");
        }
        if (end > commit_ids.size()) {
            logger.error("end is larger than the total number of commits!");
            throw new IllegalArgumentException(
                    "end is larger than the total number of commits!");
        }
        for (int i = start - 1; i < end; i++) {
            sql = "select file_id,current_file_path from actions where commit_id="
                    + commit_ids.get(i) + " and type!='D'";
            commitIdPart.add(commit_ids.get(i));
            resultSet = stmt.executeQuery(sql);
            List<List<Integer>> tmpCommit_fileIds = new ArrayList<>();
            while (resultSet.next()) {
                String current_file_path = resultSet.getString(2);
                if (current_file_path.endsWith(".java")
                        && (!current_file_path.toLowerCase().contains("test"))) {
                    List<Integer> tmp = new ArrayList<>();
                    tmp.add(commit_ids.get(i));
                    tmp.add(resultSet.getInt(1));
                    commit_fileIds.add(tmp);
                    tmpCommit_fileIds.add(tmp);
                }
            }
            for (List<Integer> tmpCommit_fileId : tmpCommit_fileIds) {
                sql = "select id,patch from patches where commit_id=" + tmpCommit_fileId.get(0) + " and "
                        + "file_id=" + tmpCommit_fileId.get(1);
                resultSet = stmt.executeQuery(sql);
                while (resultSet.next()) {
                    int commit_id = tmpCommit_fileId.get(0);
                    int file_id = tmpCommit_fileId.get(1);
                    int patch_id = resultSet.getInt(1);
                    String patch = resultSet.getString(2);
                    int offset = 0;
                    if (patch.length() == 0) {
                        logger.error("Patch is empty! commit_file_patch:" + commit_id + "_" + file_id + "_" + patch_id);
                    }
                    int sIndex = 0;
                    int eIndex = patch.substring(sIndex + 1).indexOf("@@ -");
                    while (eIndex != -1) {
                        String hunkString = patch.substring(sIndex, eIndex);
                        sIndex = eIndex;
                        eIndex = patch.substring(sIndex + 1).indexOf("@@ -");
                        List<Integer> list = new ArrayList<>();
                        list.add(commit_id);
                        list.add(file_id);
                        list.add(patch_id);
                        list.add(offset);
                        offset++;
                        commit_file_patch_offset.add(list);
                        hunksCache.put(list, hunkString);
                    }
                    List<Integer> lastHunk = new ArrayList<>();
                    lastHunk.add(commit_id);
                    lastHunk.add(file_id);
                    lastHunk.add(patch_id);
                    lastHunk.add(offset);
                    commit_file_patch_offset.add(lastHunk);
                    hunksCache.put(lastHunk, patch.substring(sIndex));
                }
            }
        }
        logger.debug("commit_file_patch_offset and length of hunks are :");
        for (List<Integer> integerList : hunksCache.keySet()) {
            StringBuilder sBuilder = new StringBuilder();
            for (Integer integer : integerList) {
                sBuilder.append(integer + " ");
            }
            logger.debug(sBuilder + ":" + hunksCache.get(integerList).length());
        }
    }

    /**
     * 根据给定的一组commit_fileId对,获取其对应的内容. 需要注意的是stringBuffer格式的content后面以逗号结尾
     *
     * @return
     * @throws SQLException
     */
    public abstract Map<List<Integer>, StringBuffer> getContentMap() throws SQLException;
}
