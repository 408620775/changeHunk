package main.extraction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class ExtractionMetrics extends Extraction {
    private Set<String> curFiles;
    private Set<String> preFiles;
    private Set<String> attributes;
    private Map<String, Map<String, Double>> grid;
    private Map<List<Integer>, StringBuffer> contentMap;
    private List<List<Integer>> commitId_fileIds;

    // 不包括id,commit_id,file_id
    /**
     * 构造函数,通过sCommitId和eCommitId确定要提取的数据的区间.
     *
     * @param database
     * @param sCommitId
     * @param eCommitId
     * @throws Exception
     */
    public ExtractionMetrics(String database, int sCommitId, int eCommitId)
            throws Exception {
        super(database, sCommitId, eCommitId);
    }

    /**
     * 获取指定范围区间内文件集合. 该集合只包含了当前文件集合,并不包含每个文件的上一次提交.每个文件的上一次提交需要根据patch恢复.
     *
     * @throws SQLException
     * @throws IOException
     */
    public void Get_icfId() throws SQLException, IOException {
        File file = new File("cfrc.txt");
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(file));
        if (start > end || start < 0) {
            bWriter.close();
            return;
        }
        int total = 0;
        int numBug = 0;
        System.out.println("the size of commit_fileIds is "+commit_fileIds.size());
        for (List<Integer> commit_fileId : commit_fileIds) {
            sql = "select extraction1.commit_id,extraction1.file_id,rev,current_file_path,bug_introducing from extraction1,"
                    + "scmlog,actions where extraction1.commit_id="
                    + commit_fileId.get(0)
                    + " and extraction1.file_id="
                    + commit_fileId.get(1)
                    + " and extraction1.commit_id=scmlog.id and extraction1.commit_id=actions.commit_id "
                    + "and extraction1.file_id=actions.file_id";
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                bWriter.append(resultSet.getInt(1) + "   " + resultSet.getInt(2)
                        + "   " + resultSet.getString(3) + "   "
                        + resultSet.getString(4));
                bWriter.append("\n");
                if (resultSet.getInt(5) == 1) {
                    numBug++;
                }
                total++;
            }
        }

        bWriter.flush();
        bWriter.close();
        System.out.println("the num of files is " + total);
        System.out.println("the num of bug is " + numBug);
        System.out.println("the ratio of bug is " + (double) numBug / total);
    }

    /**
     * 对于给定的文件集合,回复集合中每个文件的上一版本.
     *
     * @param dictory
     *            给定的当前文件组成的文件夹.
     * @throws SQLException
     * @throws IOException
     */
    public void recoverPreFile(String dictory) throws SQLException, IOException {
        File fFlie = new File(dictory);
        if (!fFlie.isDirectory()) {
            System.out.println("当前目录不是文件夹!");
            return;
        }
        String[] cFiles = fFlie.list();
        for (String string : cFiles) {
            getPreFile(dictory, string);
        }
    }

    /**
     * 根据curFile和数据库中的patch信息,恢复得到preFile.
     *
     * @param dictory
     *            文件所在的文件夹
     * @param string
     *            文件名.
     * @throws SQLException
     * @throws IOException
     */
    public void getPreFile(String dictory, String string) throws SQLException,
            IOException {
        File curFile = new File(dictory + "/" + string);
        BufferedReader bReader = new BufferedReader(new FileReader(curFile));
        int commit_id = Integer.parseInt(string.split("_")[0]);
        int file_id = Integer.parseInt(string.split("\\.")[0].split("_")[1]);
        File preFile = new File(dictory + "/" + commit_id + "_" + file_id
                + "_pre.java");
        if (!preFile.exists()) {
            preFile.createNewFile();
        }
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(preFile));
        int readIndex = 0;

        sql = "select patch from patches where commit_id=" + commit_id
                + " and file_id=" + file_id;
        resultSet = stmt.executeQuery(sql);
        String patch = null;
        while (resultSet.next()) {
            patch = resultSet.getString(1);
        }
        if (patch == null) {
            System.out.println("the patch of " + curFile + " is null");
            String line = null;
            while ((line = bReader.readLine()) != null) {
                bWriter.append(line + "\n");
            }
            bReader.close();
            bWriter.flush();
            bWriter.close();
            return;
        }
        System.out.println(curFile);
        String[] lines = patch.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("---") || lines[i].startsWith("+++")) {
                continue;
            }
            if (lines[i].startsWith("@@")) {
                String lineIndex = (String) lines[i].subSequence(
                        lines[i].indexOf("+") + 1, lines[i].lastIndexOf("@@"));

                int index = Integer.parseInt(lineIndex.split(",")[0].trim());
                int shiftP = Integer.parseInt(lineIndex.split(",")[1].trim());
                int shiftF = shiftP;

                while (readIndex < index - 1) {
                    String line = bReader.readLine();
                    bWriter.append(line + "\n");
                    readIndex++;
                }
                bWriter.flush();
                i++;

                while (i < lines.length && (!lines[i].startsWith("@@"))) {
                    if (lines[i].startsWith("-")) {
                        bWriter.append(lines[i].substring(1, lines[i].length())
                                + "\n");
                    } else if (lines[i].startsWith("+")) {

                    } else {
                        bWriter.append(lines[i] + "\n");
                    }
                    i++;
                }
                bWriter.flush();
                readIndex = readIndex + shiftF;
                for (int j = 0; j < shiftF; j++) {
                    bReader.readLine();
                }
                i = i - 1;
            }
        }

        String nextLineString = null;
        while ((nextLineString = bReader.readLine()) != null) {
            bWriter.append(nextLineString + "\n");
        }
        bReader.close();
        bWriter.flush();
        bWriter.close();
    }

    // startId和endId指的是要得到的数据的区间。如果两个参数为-1
    // 则表明对extraction1中的数据全部处理。
    /**
     * 根据understand得到的复杂度文件filename提取选择出的各实例的复杂度信息。
     *
     * @param MetricFile
     *            利用understand得到的各文件的复杂度文件，是一个单个文件。
     * @throws SQLException
     * @throws IOException
     */
    public void extraFromTxt(String MetricFile) throws SQLException,
            IOException {
        System.out.println("构建初始的复杂度标示");
        curFiles = new LinkedHashSet<>();
        preFiles = new HashSet<>();
        attributes = new LinkedHashSet<>();
        grid = new HashMap<>();
        BufferedReader bReader = new BufferedReader(new FileReader(new File(
                MetricFile)));
        String line = null;
        while ((line = bReader.readLine()) != null) {
            if (line.contains("File:")) {
                String fileName = (String) line.subSequence(
                        line.lastIndexOf('\\') + 1, line.lastIndexOf(' '));
                if (!fileName.contains("pre")) {
                    curFiles.add(fileName);
                } else {
                    preFiles.add(fileName);
                }

                while ((line = bReader.readLine()) != null
                        && (!line.contains("File:")) && (!line.equals(""))) {
                    line = line.trim();
                    String attribute = line.split(":")[0];
                    double value = Double
                            .parseDouble(line.split(":")[1].trim());
                    if (attributes.contains(attribute)) {
                        grid.get(attribute).put(fileName, value);
                    } else {
                        attributes.add(attribute);
                        Map<String, Double> temp = new HashMap<>();
                        temp.put(fileName, value);
                        grid.put(attribute, temp);
                    }
                }
            }
        }

        bReader.close();
        creatDeltMetrics();
        buildContentMap();

        // createDatabase(); // 可选择是否写入数据库
    }

    /**
     * 根据grid得到表格形式的contentMap.这里得到的数据是所有在metrics文件中出现的实例.
     *
     * @return
     */
    public void buildContentMap() {
        contentMap = new LinkedHashMap<>();
        commitId_fileIds = new ArrayList<>();
        List<Integer> title = new ArrayList<>();
        title.add(-1);
        title.add(-1);
        commitId_fileIds.add(title);
        StringBuffer titleBuffer = new StringBuffer();
        for (String attri : attributes) {
            titleBuffer.append(attri + ",");
        }
        contentMap.put(title, titleBuffer);

        for (String file : curFiles) {
            int commit_id = Integer.parseInt(file.split("_")[0]);
            int file_id = Integer.parseInt(file.substring(0, file.indexOf('.'))
                    .split("_")[1]);
            List<Integer> cf = new ArrayList<>();
            cf.add(commit_id);
            cf.add(file_id);
            commitId_fileIds.add(cf);
            StringBuffer temp = new StringBuffer();
            for (String attri : attributes) {
                if (grid.get(attri).containsKey(file)) {
                    temp.append(grid.get(attri).get(file) + ",");
                } else {
                    temp.append(0 + ",");
                }
            }
            contentMap.put(cf, temp);
        }
    }

    /**
     * 将复杂度信息写入数据库,将消耗大量时间.
     *
     * @throws SQLException
     */
    @SuppressWarnings("unused")
    private void createDatabase() throws SQLException {
        System.out.println("将复杂度数据写如数据库");
        sql = "create table extraction2(id int(11) primary key not null auto_increment,commit_id int(11),file_id int(11))";
        stmt.executeUpdate(sql);
        for (String files : curFiles) {
            int commit_id = Integer.parseInt(files.split("_")[0]);
            int file_id = Integer.parseInt(files.substring(0,
                    files.indexOf('.')).split("_")[1]);
            sql = "insert extraction2 (commit_id,file_id) values(" + commit_id
                    + "," + file_id + ")";
            stmt.executeUpdate(sql);
        }
        for (String attr : attributes) {
            sql = "alter table extraction2 add column " + attr
                    + " float default 0";
            stmt.executeUpdate(sql);
            for (String file : curFiles) {
                int commit_id = Integer.parseInt(file.split("_")[0]);
                int file_id = Integer.parseInt(file.substring(0,
                        file.indexOf('.')).split("_")[1]);
                Double value = grid.get(attr).get(file);
                if (value == null) {
                    value = 0.0;
                }
                sql = "update extraction2 set " + attr + "=" + value
                        + " where commit_id=" + commit_id + " and file_id="
                        + file_id;
                stmt.executeUpdate(sql);
            }
        }

    }

    /**
     * 根据understand得到的复杂度信息提取DeltMetrics。
     *
     * @throws SQLException
     */
    public void creatDeltMetrics() throws SQLException {
        System.out.println("构造delta复杂度");
        Set<String> deltaArrSet = new HashSet<>();
        for (String attribute : attributes) {
            String deltaAttri = attribute + "_delta";
            deltaArrSet.add(deltaAttri);

            Map<String, Double> deltaMap = new HashMap<>();
            for (String cur : curFiles) {
                String preName = cur.substring(0, cur.indexOf('.'))
                        + "_pre.java";
                double value1 = 0;
                if (grid.get(attribute).containsKey(cur)) {
                    value1 = grid.get(attribute).get(cur);
                }
                double value2 = 0;
                if (grid.get(attribute).containsKey(preName)) {
                    value2 = grid.get(attribute).get(preName);
                }
                double delta = value1 - value2;
                deltaMap.put(cur, delta);
            }
            grid.put(deltaAttri, deltaMap);
        }
        attributes.addAll(deltaArrSet);
    }

    @Override
    public Map<List<Integer>, StringBuffer> getContentMap(
            List<List<Integer>> someCommit_fileIds) throws SQLException {
        if (contentMap==null) {
            System.out.println("you need to run extraFromTxt first!");
            return null;
        }
        Map<List<Integer>, StringBuffer> content=new LinkedHashMap<>();
        List<Integer> title = new ArrayList<>();
        title.add(-1);
        title.add(-1);
        content.put(title, contentMap.get(title));
        for (List<Integer> list : someCommit_fileIds) {
            content.put(list, contentMap.get(list));
        }
        return content;
    }

    public static void main(String[] args) throws Exception {
        ExtractionMetrics extractionMetrics =new ExtractionMetrics("MyFlink",1001, 1300);
        extractionMetrics.recoverPreFile("flinkFiles");
    }
}
