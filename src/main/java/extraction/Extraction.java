package extraction;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    String sql;
    Statement stmt;
    ResultSet resultSet;
    int start;
    int end;
    List<List<Integer>> commit_fileIds;
    List<Integer> commit_ids;
    List<Integer> commitIdPart;
    List<List<Integer>> commit_file_hunkIds;
    SQLConnection sqlL;
    String databaseName;

    public Extraction(String database, int start, int end) throws IOException, SQLException {
        this.start = start;
        this.end = end;
        this.databaseName = database;
        this.sqlL = new SQLConnection(database);
        this.stmt = sqlL.getStmt();
        initialKeys();
    }

    /**
     * 根据给定的范围获取有效的commit_file对,也就是包括删除在内的记录对.
     *
     * @throws Exception
     */
    private void initialKeys() throws IllegalArgumentException, SQLException, IOException {
        System.out.println("Initial keys!");
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

}
