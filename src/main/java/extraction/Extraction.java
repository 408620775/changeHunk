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
    public static String sql;
    public static Statement stmt;
    public static ResultSet resultSet;
    public static int start;
    public static int end;
    public static List<List<Integer>> commit_fileIds;
    public static List<Integer> commit_ids;
    public static List<Integer> commitIdPart;
    public static List<List<Integer>> commit_file_hunkIds;
    public static List<Integer> title = Arrays.asList(-1, -1, -1);
    public static SQLConnection sqlL;
    public static String databaseName;
    public static String metaTableName = "metaHunk";
    public static String metaTableNamekey = "MetaTableName";
    public static boolean hasLoadProperty = false;
    public static String hunksCacheKey = "cacheHunks";
    public static String boolPropertyYes = "Yes";
    public static Map<Integer, List<Integer>> hunksCache;
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
        if (properties.containsKey(hunksCacheKey)) {
            if (properties.getProperty(hunksCacheKey).equals(boolPropertyYes)) {
                getHunksCashe();
            }
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
        commit_file_hunkIds = new ArrayList<>();
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
                sql = "select id from hunks where commit_id=" + tmpCommit_fileId.get(0) + " and "
                        + "file_id=" + tmpCommit_fileId.get(1);
                resultSet = stmt.executeQuery(sql);
                while (resultSet.next()) {
                    List<Integer> list = new ArrayList<>();
                    list.add(tmpCommit_fileId.get(0));
                    list.add(tmpCommit_fileId.get(1));
                    list.add(resultSet.getInt(1));
                    commit_file_hunkIds.add(list);
                }
            }
        }
        logger.info("commit_fileIds are :");
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < commit_fileIds.size(); i++) {
            sBuilder.append("[" + commit_fileIds.get(i).get(0) + "," + commit_fileIds.get(i).get(1) + "] ");
            if (i % 9 == 0 && i != 0) {
                logger.info(sBuilder);
                sBuilder = new StringBuilder();
            }
        }
    }

    /**
     * 按时间序返回有效的commit_fileId列表。
     *
     * @return 按时间排序的指定范围内的commit_id列表。
     */
    public List<List<Integer>> getCommit_FileIds() {
        return commit_fileIds;
    }

    /**
     * 根据给定的一组commit_fileId对,获取其对应的内容. 需要注意的是stringBuffer格式的content后面以逗号结尾
     *
     * @return
     * @throws SQLException
     */
    public abstract Map<List<Integer>, StringBuffer> getContentMap(
            List<List<Integer>> someCommit_fileIds) throws SQLException;

    public void getHunksCashe() throws SQLException {
        for (List<Integer> commit_file_hunkId : commit_file_hunkIds) {
            sql = "select old_start_line, old_end_line,new_start_line,new_end_line from hunks where " +
                    "id=" + commit_file_hunkId.get(2);
            resultSet = stmt.executeQuery(sql);
            resultSet.next();
            List<Integer> list = new ArrayList<>();
            list.add(resultSet.getInt(1));
            list.add(resultSet.getInt(2));
            list.add(resultSet.getInt(3));
            list.add(resultSet.getInt(4));
            hunksCache.put(commit_file_hunkId.get(2), list);
        }
    }
}
