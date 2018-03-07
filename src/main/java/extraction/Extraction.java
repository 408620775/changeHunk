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
    public static String hunkStringStartFlag = "@@ -";
    public static boolean hasLoadProperty = false;
    public static Statement stmt;
    public static ResultSet resultSet;
    public static SQLConnection sqlL;
    public static List<Integer> commits;
    public static List<Integer> commit_parts;
    public static List<Integer> titleIndex = Arrays.asList(-1, -1, -1, -1);
    public static List<String> titleName = Arrays.asList("commit_id", "file_id", "patch_id", "offset");
    public static List<List<Integer>> commit_file_parts;
    public static List<List<Integer>> commit_files;
    public static List<List<Integer>> commit_file_patch_offset_part;
    public static List<List<Integer>> commit_file_patch_offsets;
    public static Map<List<Integer>, String> hunks_cache_part;
    public static String databasePropertyPath = "src/main/resources/database.properties";

    public Extraction(String database, int start, int end) throws IOException, SQLException {
        logger.info("Extract " + database + " information. The start commit_id is " + start + " and the end commit_id is " + end);
        this.start = start;
        this.end = end;
        this.databaseName = database;
        this.sqlL = SQLConnection.getConnection(database, databasePropertyPath);
        this.stmt = sqlL.getStmt();
        if (commits == null) {
            initialKeys();
        }
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
        commits = new ArrayList<>();
        commit_parts = new ArrayList<>();
        commit_file_parts = new ArrayList<>();
        commit_files = new ArrayList<>();
        commit_file_patch_offset_part = new ArrayList<>();
        commit_file_patch_offsets = new ArrayList<>();
        hunks_cache_part = new LinkedHashMap<>();
        List<Integer> all_commits = new ArrayList<>();
        sql = "select id from scmlog order by commit_date";
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            all_commits.add(resultSet.getInt(1));
        }
        if (start < 0) {
            logger.error("start commit_id can't be less 0!");
            throw new IllegalArgumentException("start commit_id can't be less 0!");
        }
        if (end > all_commits.size()) {
            logger.error("end is larger than the total number of commits!");
            throw new IllegalArgumentException(
                    "end is larger than the total number of commits!");
        }
        for (int i = 0; i < all_commits.size(); i++) {
            sql = "select file_id,current_file_path from actions where commit_id="
                    + all_commits.get(i) + " and type!='D'";
            boolean vaildOperation = false;
            resultSet = stmt.executeQuery(sql);
            List<List<Integer>> tmpCommit_fileIds = new ArrayList<>();
            while (resultSet.next()) {
                boolean vaildFile_id = false;
                String current_file_path = resultSet.getString(2);
                if (current_file_path.endsWith(".java")
                        && (!current_file_path.toLowerCase().contains("test"))) {
                    vaildOperation = true;
                    vaildFile_id = true;
                }
                if (vaildFile_id) {
                    List<Integer> tmp = new ArrayList<>();
                    tmp.add(all_commits.get(i));
                    tmp.add(resultSet.getInt(1));
                    commit_files.add(tmp);
                    tmpCommit_fileIds.add(tmp);
                    if (i + 1 >= start && i + 1 <= end) {
                        commit_file_parts.add(tmp);
                    }
                }
            }
            if (vaildOperation) {
                commits.add(all_commits.get(i));
                if (i + 1 >= start && i + 1 <= end) {
                    commit_parts.add(all_commits.get(i));
                }
            }
            for (List<Integer> tmpCommit_fileId : tmpCommit_fileIds) {
                sql = "select id,patch from patches where commit_id=" + tmpCommit_fileId.get(0) + " and "
                        + "file_id=" + tmpCommit_fileId.get(1);
                resultSet = stmt.executeQuery(sql);
                int commit_id = tmpCommit_fileId.get(0);
                int file_id = tmpCommit_fileId.get(1);
                while (resultSet.next()) {
                    int patch_id = resultSet.getInt(1);
                    String patch = resultSet.getString(2).trim();
                    List<String> hunkStrings = parsePatchString(patch, commit_id, file_id, patch_id);
                    for (int j = 0; j < hunkStrings.size(); j++) {
                        List<Integer> keys = new ArrayList<>();
                        keys.add(commit_id);
                        keys.add(file_id);
                        keys.add(patch_id);
                        keys.add(j);
                        commit_file_patch_offsets.add(keys);
                        if (i + 1 >= start && i + 1 <= end) {
                            commit_file_patch_offset_part.add(keys);
                            hunks_cache_part.put(keys, hunkStrings.get(j));
                        }
                    }

                }
            }
        }
        logger.info("The total number of various types commit is:" + all_commits.size());
        logger.info("The total number of Java commit is:" + commits.size());
        logger.info("The total number of instances is:" + commit_file_patch_offsets.size());
        logger.info("The selected commit order range is: [" + start + "," + end + "]");
        logger.info("The number of effective Java commits to be considered is:" + commit_parts.size());
        logger.info("The number of effective instances to be considered is:" + commit_file_patch_offset_part.size());
    }

    public static List<String> parsePatchString(String patch, int commit_id, int file_id, int patch_id) {
        if (patch.length() == 0) {
            logger.error("Patch is empty! commit_file_patch:" + commit_id + "_" + file_id + "_" + patch_id);
            return new ArrayList<>();
        }
        List<String> hunkStrings = new ArrayList<>();
        if (!patch.contains(hunkStringStartFlag)) {
            logger.error("Patch content don't contain \"@@ -\",commit_id=" + commit_id + ",file_id=" + file_id + ","
                    + "patch_id=" + patch_id);
            return new ArrayList<>();
        }
        int sIndex = patch.indexOf(hunkStringStartFlag);
        int eIndex = patch.substring(sIndex + 1).indexOf(hunkStringStartFlag) + sIndex + 1;
        while (eIndex != sIndex) {
            String hunkString = patch.substring(sIndex, eIndex).trim();
            if (!hunkString.startsWith(hunkStringStartFlag)) {
                logger.error("Split patches error! commit_id=" + commit_id + " and file_id=" + file_id);
                logger.error("hunkString is :" + hunkString);
            }
            hunkStrings.add(hunkString);
            sIndex = eIndex;
            eIndex = patch.substring(sIndex + 1).indexOf(hunkStringStartFlag) + sIndex + 1;
            List<Integer> list = new ArrayList<>();
        }
        String hunkString = patch.substring(sIndex).trim();
        hunkStrings.add(hunkString);
        return hunkStrings;
    }

    public static int[] parseHunkRange(String hunkString) {
        String range = hunkString.substring(hunkString.indexOf("-") + 1, hunkString.lastIndexOf("@@"))
                .replace(",", " ").replace("+", "");
        String[] numString = range.split(" ");
        int neg_hunk_start_index = Integer.parseInt(numString[0]);
        int neg_hunk_offset_index = Integer.parseInt(numString[1]);
        int pos_hunk_start_index = Integer.parseInt(numString[2]);
        int pos_hunk_offset_index = Integer.parseInt(numString[3]);
        int[] res = new int[]{neg_hunk_start_index, neg_hunk_offset_index, pos_hunk_start_index,
                pos_hunk_offset_index};
        return res;
    }

    /**
     * 根据给定的一组commit_fileId对,获取其对应的内容. 需要注意的是stringBuffer格式的content后面以逗号结尾
     *
     * @return
     * @throws SQLException
     */
    public abstract Map<List<Integer>, StringBuffer> getContentMap() throws SQLException;

    public String underLineFormat(List<Integer> list) {
        if (list == null || list.size() == 0) {
            return "";
        }
        StringBuffer sBuffer = new StringBuffer();
        sBuffer.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sBuffer.append("_" + list.get(i));
        }
        return sBuffer.toString();
    }
}
