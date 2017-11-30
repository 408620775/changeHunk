package extraction;

import org.apache.log4j.Logger;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;

/**
 * 从miningit生成的数据库中提取一些基本信息，例如作者姓名，提交时间，累计的bug计数等信息。 构造函数中提供需要连接的数据库。
 * 根据指定的范围获取commit_id列表（按照时间顺序）。通过对各表的操作获取一些基本数据。
 * 除了基本表，miningit还需执行extension=bugFixMessage，metrics
 *
 * @author niu
 */
public final class ExtractionMeta extends Extraction {
    private static Logger logger = Logger.getLogger(ExtractionMeta.class);
    private List<String> curAttributes;
    private List<List<Integer>> commit_file_inExtracion1;
    private String message;

    /**
     * 提取第一部分change info，s为指定开始的commit_id，e为结束的commit_id
     *
     * @param database 指定的miningit生成数据的数据库。
     * @param s        指定的commit的起始值,从1开始算.
     * @param e        指定的commit的结束值
     * @throws Exception
     */
    public ExtractionMeta(String database, int s, int e) throws Exception {
        super(database, s, e);
    }

    /**
     * 生成mataTable表,并将相关数据填入表中.
     */
    public void getMetaTableData() throws Exception {
        long sTime = System.currentTimeMillis();
        CreateTable();
        long eTime = System.currentTimeMillis();
        logger.info("CreateTable() cost time:" + (eTime - sTime));
        sTime = eTime;
        initial();
        eTime = System.currentTimeMillis();
        logger.info("initial() cost time:" + (eTime - sTime));
        sTime = eTime;
        bug_introducing();
        eTime = System.currentTimeMillis();
        System.out.println("bug_introducing() cost time:" + (eTime - sTime));
        sTime = eTime;
        cumulative_bug_count();
        eTime = System.currentTimeMillis();
        System.out.println("cumulative_bug_count() cost time:" + (eTime - sTime));
        sTime = eTime;
        cumulative_change_count();
        eTime = System.currentTimeMillis();
        System.out.println("cumulative_change_count() cost time:" + (eTime - sTime));
        sTime = eTime;
        author_name(false);
        eTime = System.currentTimeMillis();
        System.out.println("author_name() cost time:" + (eTime - sTime));
        sTime = eTime;
        commit_day(false);
        eTime = System.currentTimeMillis();
        System.out.println("commit_day() cost time:" + (eTime - sTime));
        sTime = eTime;
        commit_hour(false);
        eTime = System.currentTimeMillis();
        System.out.println("commit_hour() cost time:" + (eTime - sTime));
        sTime = eTime;
        change_log_length(false);
        eTime = System.currentTimeMillis();
        System.out.println("change_log_length() cost time:" + (eTime - sTime));
        sTime = eTime;
        changed_LOC(false);
        eTime = System.currentTimeMillis();
        System.out.println("changed_LOC() cost time:" + (eTime - sTime));
    }

    /**
     * 创建数据表。 若构造函数中所连接的数据库中已经存在该表，则会产生冲突。
     * 解决方案有2：（1）若之前的表为本程序生成的表，则可将其卸载。
     * （2）若之前的表为用户自己的私有的，则可考虑备份原表的数据，并删除原表（建议），
     * 或者重命名本程序中的metaTable的名称（不建议）。
     *
     * @throws SQLException
     */
    public void CreateTable() throws SQLException {
        sql = "show tables";
        resultSet = stmt.executeQuery(sql);
        boolean alreadyExist = false;
        while (resultSet.next()) {
            if (resultSet.getString(1).equals(metaTableName)) {
                logger.info("Already has the table: " + metaTableName);
                logger.info("Table will be uninstalled");
                alreadyExist = true;
                break;
            }
        }
        if (alreadyExist) {
            sql = "DROP TABLE " + metaTableName;
            int result = stmt.executeUpdate(sql);
            if (result != -1) {
                logger.info("Drop table successfully.");
            } else {
                logger.error("Drop table " + metaTableName + " fail.");
                throw new SQLException("Drop table " + metaTableName + " fail.");
            }
        }
        sql = "create table " + metaTableName + "(id int(11) primary key not null auto_increment,commit_id int(11),"
                + "file_id int(11), patch_id int(11), offset int(11), author_name varchar(40),commit_day varchar(15),"
                + "commit_hour int(2),cumulative_change_count int(15) default 0,cumulative_bug_count int(15) default 0,"
                + "change_log_length int(5),changed_LOC int(7),sloc int(7),bug_introducing tinyint(1) default 0)";
        int result = stmt.executeUpdate(sql);
        if (result != -1) {
            logger.info("Create mateTable successfully.");
        } else {
            logger.error("Failed to create mateTable.");
            throw new SQLException("Failed to create mateTable.");
        }
    }

    /**
     * 初始化表格。 根据指定范围内的按时间排序的commit列表（commit_ids）初始化metaTable。
     * 初始化内容包括id，commit_id，file_id,hunk_id。需要注意的是，目前只考虑java文件，且不考虑java中的测试文件
     * 所以在actions表中选择对应的项时需要进行过滤。由于SZZ算法需要回溯,所以在初始化metaTable表的时候需要考虑所有actions.
     *
     * @throws SQLException
     */

    public void initial() throws SQLException {
        logger.info("initial the table");
        for (List<Integer> key : hunks_indexs.keySet()) {
            sql = "insert " + metaTableName + " (commit_id,file_id,patch_id,offset) values("
                    + key.get(0) + "," + key.get(1) + "," + key.get(2) + key.get(3) + ")";
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 查看当前metaTable表中所有已存在的属性,对外的接口.
     *
     * @return
     * @throws SQLException
     */
    public List<String> getCurAttributes() throws SQLException {
        if (curAttributes == null) {
            obtainCurAttributes();
        }
        return curAttributes;
    }

    /**
     * 将metaTable表中现有的属性填入curAttributes.
     *
     * @throws SQLException
     */
    private void obtainCurAttributes() throws SQLException {
        if (curAttributes == null) {
            curAttributes = new ArrayList<>();
            sql = "desc " + metaTableName;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                curAttributes.add(resultSet.getString(1));
            }
        }
    }

    /**
     * 获取作者姓名。如果excuteAll为真,则获取metaTable中所有数据的作者.
     * 否则只获取commit_id在commitIdPart中的数据的作者.
     *
     * @throws SQLException
     */
    public void author_name(boolean excuteAll) throws SQLException {
        List<Integer> excuteList;
        if (excuteAll) {
            excuteList = commits;
        } else {
            excuteList = commit_parts;
        }
        System.out.println("get author_name");
        for (Integer integer : excuteList) {
            sql = "update " + metaTableName + ",scmlog,people set " + metaTableName + ".author_name=people.name where "
                    + metaTableName + ".commit_id=" + integer + " and " + metaTableName + ".commit_id=scmlog.id and "
                    + "scmlog.author_id=people.id";
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取提交的日期，以星期标示。如果excuteAll为真,则获取metaTable中所有数据的日期.
     * 否则只获取commit_id在commitIdPart中的数据的日期.
     *
     * @throws SQLException
     */
    public void commit_day(boolean excuteAll) throws SQLException {
        System.out.println("get commit_day");
        List<Integer> excuteList;
        if (excuteAll) {
            excuteList = commits;
        } else {
            excuteList = commit_parts;
        }
        Map<Integer, String> mapD = new HashMap<>(); // 加入修改日期
        for (Integer integer : excuteList) {
            sql = "select id,commit_date from scmlog where id=" + integer;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                mapD.put(resultSet.getInt(1),
                        resultSet.getString(2).split(" ")[0]);
            }
        }

        // System.out.println(mapD.size()); //测试是否提取出时间，结果正确
        Calendar calendar = Calendar.getInstance();// 获得一个日历
        String[] str = {"Sunday", "Monday", "Tuesday", "Wednesday",
                "Thursday", "Friday", "Saturday",};
        for (Integer i : mapD.keySet()) {
            int year = Integer.parseInt(mapD.get(i).split("-")[0]);
            int month = Integer.parseInt(mapD.get(i).split("-")[1]);
            int day = Integer.parseInt(mapD.get(i).split("-")[2]);

            calendar.set(year, month - 1, day);// 设置当前时间,月份是从0月开始计算
            int number = calendar.get(Calendar.DAY_OF_WEEK);// 星期表示1-7，是从星期日开始，
            mapD.put(i, str[number - 1]);
            sql = "update " + metaTableName + " set commit_day=\" " + str[number - 1]
                    + "\" where commit_id=" + i;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取提交的时间，以小时标示。如果excuteAll为真,则获取metaTable中所有数据的时间.
     * 否则只获取commit_id在commitIdPart中的数据的时间.
     *
     * @throws NumberFormatException
     * @throws SQLException
     */
    public void commit_hour(boolean excuteAll) throws NumberFormatException,
            SQLException {
        System.out.println("get commit_hour");
        List<Integer> excuteList;
        if (excuteAll) {
            excuteList = commits;
        } else {
            excuteList = commit_parts;
        }
        Map<Integer, Integer> mapH = new HashMap<>(); // 加入修改时间
        for (Integer integer : excuteList) {
            sql = "select id,commit_date from scmlog where id=" + integer;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                mapH.put(resultSet.getInt(1), Integer.parseInt(resultSet
                        .getString(2).split(" ")[1].split(":")[0]));
            }
        }

        Iterator<Entry<Integer, Integer>> iter = mapH.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Integer, Integer> e = iter.next();
            int key = e.getKey();
            int value = e.getValue();
            sql = "update  " + metaTableName + " set commit_hour=" + value
                    + "  where commit_id=" + key;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取changlog的长度。如果excuteAll为真,则获取extraction1中所有数据的changlog长度.
     * 否则只获取commit_id在commitIdPart中的数据的changelog长度.
     *
     * @throws SQLException
     */
    public void change_log_length(boolean excuteAll) throws SQLException {
        System.out.println("get change log length");
        List<Integer> excuteList;
        if (excuteAll) {
            excuteList = commits;
        } else {
            excuteList = commit_parts;
        }
        for (Integer integer : excuteList) {
            sql = "select message from scmlog where id=" + integer;
            resultSet = stmt.executeQuery(sql);
            String message = null;
            while (resultSet.next()) {
                message = resultSet.getString(1);
            }
            sql = "update " + metaTableName + " set change_log_length ="
                    + message.length() + " where commit_id=" + integer;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取源码长度。 得到表metrics的复杂度开销很大，
     * 而得到的信息在此后的extraction2中非常方便的提取，所以真心觉得此处提起这个度量没有什么意义。
     * <p>
     * 但是由于需要获取size()中的lt属性值,还必须得有sloc才好算.而extraction2写入数据库的时间成本过高,所以在merger时,
     * extraction2的信息往往是根据metrics的txt直接生成的,而非从数据库中提取.所以本方法也必须依赖于metrics
     * txt来获取sloc的值.
     *
     * @throws SQLException
     * @throws IOException
     */
    public void sloc(String metricsTxt) throws SQLException, IOException {
        File metricsFile = new File(metricsTxt);
        BufferedReader bReader = new BufferedReader(new FileReader(metricsFile));
        String line;
        String metric;
        Map<List<Integer>, Integer> countLineCode = new HashMap<>();
        while ((line = bReader.readLine()) != null) {
            if ((!line.contains("pre")) && line.contains(".java")) {
                String commit_file_id = line.substring(
                        line.lastIndexOf("\\") + 1, line.lastIndexOf("."));
                int commitId = Integer.parseInt(commit_file_id.split("_")[0]);
                int fileId = Integer.parseInt(commit_file_id.split("_")[1]);
                List<Integer> key = new ArrayList<>();
                key.add(commitId);
                key.add(fileId);
                while ((metric = bReader.readLine()) != null) {
                    if (metric.contains("CountLine")) {
                        countLineCode.put(key,
                                Integer.parseInt(metric.split(":")[1].trim()));
                        break;
                    }
                }
            }
        }
        bReader.close();
        for (List<Integer> key : countLineCode.keySet()) {
            sql = "UPDATE extraction1 SET sloc=" + countLineCode.get(key)
                    + " where commit_id=" + key.get(0) + " and file_id="
                    + key.get(1);
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取累计的bug计数。首先得判断出某个commit_id，file_id对应的那个文件是否是bug_introducing。
     * 也就是本程序需要在bug_introducing之后执行.
     *
     * @throws Exception 主要是为了想体验一下这个异常怎么用才加的，其实没啥用，因为bug_introducing非常不可能出现除0,1外的其他值。
     */
    // FIXME 此函数也需要在resources包中的history.py中实现.但是目前还没有发现其实现办法.
    public void cumulative_bug_count() throws Exception {
        System.out.println("get cumulative bug count");
        sql = "select count(*) from " + metaTableName;
        resultSet = stmt.executeQuery(sql);
        int totalNum = 0;
        while (resultSet.next()) {
            totalNum = resultSet.getInt(1);
        }
        Map<String, Integer> fileName_curBugCount = new HashMap<>();
        for (int i = 1; i <= totalNum; i++) {
            sql = "select file_name,bug_introducing from files," + metaTableName + " where file_id=files.id and " +
                    metaTableName + ".id=" + i;
            resultSet = stmt.executeQuery(sql);
            String file_name = null;
            int bug_introducing = 0;
            while (resultSet.next()) {
                file_name = resultSet.getString(1);
                bug_introducing = resultSet.getInt(2);
            }
            if (bug_introducing == 1) {
                if (fileName_curBugCount.containsKey(file_name)) {
                    fileName_curBugCount.put(file_name,
                            fileName_curBugCount.get(file_name) + 1);
                } else {
                    fileName_curBugCount.put(file_name, 1);
                }
            } else if (bug_introducing == 0) {
                if (!fileName_curBugCount.containsKey(file_name)) {
                    fileName_curBugCount.put(file_name, 0);
                }
            } else {
                Exception e = new Exception(
                        "class label is mistake! not 1 and not 0");
                e.printStackTrace();
                throw e;
            }
            sql = "update " + metaTableName + " set cumulative_bug_count="
                    + fileName_curBugCount.get(file_name) + " where id=" + i;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取累计的change计数。此函数版本是最初根据文件名来确定文件的历史更改的,这样做相对粗糙,精细的实现形式已经在history.py中实现,
     * 此处暂时废弃.
     *
     * @throws SQLException
     */
    @SuppressWarnings("unused")
    private void cumulative_change_count() throws SQLException {
        System.out.println("get cumulative change count");
        sql = "select count(*) from " + metaTableName;
        resultSet = stmt.executeQuery(sql);
        int totalNum = 0;
        while (resultSet.next()) {
            totalNum = resultSet.getInt(1);
        }
        Map<String, Integer> fileName_curChangeCount = new HashMap<>();

        for (int i = 1; i <= totalNum; i++) {
            sql = "select file_name from files," + metaTableName + " where file_id=files.id and " + metaTableName + ".id="
                    + i;
            resultSet = stmt.executeQuery(sql);
            String file_name = null;
            while (resultSet.next()) {
                file_name = resultSet.getString(1);
            }
            if (fileName_curChangeCount.containsKey(file_name)) {
                fileName_curChangeCount.put(file_name,
                        fileName_curChangeCount.get(file_name) + 1);
            } else {
                fileName_curChangeCount.put(file_name, 1);
            }
            sql = "update " + metaTableName + " set cumulative_change_count="
                    + fileName_curChangeCount.get(file_name) + " where id=" + i;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 获取改变的代码的长度。 主要从hunks中提取数据，如果在miningit中hunks运行两遍会导致hunks中数据有问题，出现重复项。
     * 数据库中为null的项取出的数值是0,而不是空。
     *
     * @throws SQLException
     */
    public void changed_LOC(boolean excuteAll) throws SQLException {
        System.out.println("get changed loc");
        List<Integer> excuteList;
        if (excuteAll) {
            excuteList = commits;
        } else {
            excuteList = commit_parts;
        }
        List<Integer> hunk_ids = new ArrayList<>();
        for (Integer integer : excuteList) {
            sql = "select hunk_id from " + metaTableName + " where commit_id="
                    + integer;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                int hunk_id = resultSet.getInt(1);
                hunk_ids.add(hunk_id);
            }
        }
        for (Integer hunk_id : hunk_ids) {
            sql = "select old_start_line,old_end_line,new_start_line,new_end_line from hunks where id="
                    + hunk_id;
            resultSet = stmt.executeQuery(sql);
            int changeLoc = 0;
            while (resultSet.next()) {
                if (resultSet.getInt(1) != 0) {
                    changeLoc = changeLoc + resultSet.getInt(2)
                            - resultSet.getInt(1) + 1;
                }
                if (resultSet.getInt(3) != 0) {
                    changeLoc = changeLoc + resultSet.getInt(4)
                            - resultSet.getInt(3) + 1;
                }
            }
            sql = "update " + metaTableName + " set changed_LOC=" + changeLoc
                    + " where hunk_id=" + hunk_id;
            stmt.executeUpdate(sql); // 这个信息，似乎在extraction2中的detal计算时已经包含了啊。
        }
    }

    /**
     * bug_introducing函数,使用SZZ算法标记每个实例的类标签.需要注意的是在用SZZ回溯最近的一次更改的时候,
     * 使用的是file_name判定的,因为分支不同的话file_id基本不同,所以此时用file_name更好.
     *
     * @throws SQLException
     */
    public void bug_introducing() throws SQLException {
        logger.info("get bug introducing");
        Map<Integer, Boolean> commitId_isBugFix = new HashMap<>();
        for (List<Integer> commit_file : commit_files) {
            int commit_id = commit_file.get(0);
            int file_id = commit_file.get(1);
            boolean is_bug_fix = false;
            if (commitId_isBugFix.containsKey(commit_id)) {
                is_bug_fix = commitId_isBugFix.get(commit_id);
            } else {
                sql = "select is_bug_fix from scmlog where id=" + commit_id;
                while (resultSet.next()) {
                    if (resultSet.getInt(1) == 1) {
                        is_bug_fix = true;
                    }
                }
                commitId_isBugFix.put(commit_id, is_bug_fix);
            }
            if (!is_bug_fix) {
                continue;
            }
            sql = "select id,old_start_line,old_end_line,new_start_line,new_end_line from hunks where commit_id="
                    + commit_id + " and file_id=" + file_id;
            List<List<Integer>> fake_hunk_id_ranges = new ArrayList<>();
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                if (resultSet.getInt(3) == 0) { //old_end_line is 0, without a corresponding line.
                    continue;
                }
                List<Integer> id_lineIndex = new ArrayList<>();
                id_lineIndex.add(resultSet.getInt(1));
                id_lineIndex.add(resultSet.getInt(2));
                id_lineIndex.add(resultSet.getInt(3));
                id_lineIndex.add(resultSet.getInt(4));
                id_lineIndex.add(resultSet.getInt(5));
                fake_hunk_id_ranges.add(id_lineIndex);
            }
            for (List<Integer> fake_hunk_id_range : fake_hunk_id_ranges) {
                sql = "select bug_commit_id from hunk_blames where hunk_id=" + fake_hunk_id_range.get(0);
                int bug_commit_id = -1;
                resultSet = stmt.executeQuery(sql);
                while (resultSet.next()) {
                    bug_commit_id = resultSet.getInt(1);
                }
                if (bug_commit_id == -1) {
                    logger.debug("bug_commit_id for fake_hunk " + fake_hunk_id_range + " is empty but the commit which contains" +
                            " fake_hunk " + fake_hunk_id_range + " is bug_fix!");
                    continue;
                }
                sql = "select patch_id,offset from " + metaTableName + " where commit_id=" + bug_commit_id + " and file_id=" + file_id;
                resultSet = stmt.executeQuery(sql);
                List<Integer> matchKey = null;
                while (resultSet.next()) {
                    List<Integer> key = new ArrayList<>();
                    key.add(bug_commit_id);
                    key.add(file_id);
                    key.add(resultSet.getInt(1));
                    key.add(resultSet.getInt(2));
                    if (!hunks_indexs.containsKey(key)) {
                        logger.error("hunks_index don't contain the key:" + underLineFormat(key));
                        continue;
                    }
                    int[] start_offset = hunks_indexs.get(key);
                    if (fake_hunk_id_range.get(1) >= start_offset[2] && fake_hunk_id_range.get(2)
                            <= start_offset[2] + start_offset[3]) {
                        matchKey = key;
                        break;
                    }
                }
                if (matchKey == null) {
                    logger.error("fake_hunk_id:" + fake_hunk_id_range.get(0) + " don't hava a matched old hunk!");
                }
                //need up speed.
                sql = "update " + metaTableName + " set bug_introducing=1 where commit_id=" + matchKey.get(0) + " and "
                        + "file_id=" + matchKey.get(1) + " and patch_id=" + matchKey.get(2) + " and offset=" + matchKey.get(3);
                stmt.executeUpdate(sql);
            }
        }
    }

    /**
     * 根据论文A Large-Scale Empirical Study Of Just-in-Time Quality Assurance,增加分类实例的某些属性
     * .history属性的实现中,首先用到的是之前根据文件名或者file_id回找一个文件的历史记录,对于于history1
     * ()的实现.后来发现可以根据gitlog来更加准确的获取文件的历史改变记录
     * ,对应于histor2()的实现,history2()调用resource中的history.py
     *
     * @throws SQLException
     * @throws ParseException
     * @throws InterruptedException
     * @throws IOException
     */
    public void just_in_time(String gitFile) throws SQLException,
            ParseException, IOException, InterruptedException {
        diffusion();
        size(false);
        purpose(false);
        history(gitFile);
    }

    /**
     * 根据论文A Large-Scale Empirical Study Of Just-in-Time Quality
     * Assurance,增加分类实例的diffusion(传播)属性.包括NS,ND,NF和Entropy四类.
     * 具体信息可参考论文中的定义.起始根据其实现,感觉此函数是针对commitId的,而非(commitId,fileId)对.
     * 此函数如果执行所有实例,那么它还依赖changed_LOC是否执行了所有函数.
     *
     * @throws SQLException
     */
    public void diffusion() throws SQLException {
        System.out.println("Update Diffusion");
        if (curAttributes == null) {
            obtainCurAttributes();
        }
        if (!curAttributes.contains("ns")) {
            sql = "alter table " + metaTableName + " add (ns int(4),nd int(4),nf int(4),entropy float)";
            stmt.executeUpdate(sql);
            curAttributes.add("ns");
            curAttributes.add("nd");
            curAttributes.add("nf");
            curAttributes.add("entropy");
        }
        for (Integer commit_id : commit_parts) {
            Set<String> subsystem = new HashSet<>();
            Set<String> directories = new HashSet<>();
            Set<String> files = new HashSet<>();
            sql = "select current_file_path from actions where commit_id="
                    + commit_id;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                String pString = resultSet.getString(1);
                if ((!pString.endsWith(".java"))
                        || pString.toLowerCase().contains("test")) {
                    continue;
                }
                String[] path = pString.split("/");
                files.add(path[path.length - 1]);
                if (path.length > 1) {
                    subsystem.add(path[0]);
                    directories.add(path[path.length - 2]);
                }
            }
            sql = "select changed_LOC from " + metaTableName + " where commit_id="
                    + commit_id;
            resultSet = stmt.executeQuery(sql);
            List<Integer> changeOfFile = new ArrayList<>();
            while (resultSet.next()) {
                changeOfFile.add(resultSet.getInt(1));
            }
            // 如果为没有相对应的更改的文件,说明该commit很有可能没有更改java文件,或者其修改的java文件都是test类型的.
            if (changeOfFile.size() == 0) {
                continue;
            }
            float entropy = MyTool.calEntropy(changeOfFile);
            float maxEntropy = (float) (Math.log(changeOfFile.size()) / Math
                    .log(2));
            if (Math.abs(maxEntropy - 0) < 0.0001) {
                entropy = 0;
            } else {
                entropy = entropy / maxEntropy;
            }
            sql = "UPDATE " + metaTableName + " SET ns=" + subsystem.size() + ",nd="
                    + directories.size() + ",nf=" + files.size() + ",entropy="
                    + entropy + " where commit_id=" + commit_id;
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 根据论文A Large-Scale Empirical Study Of Just-in-Time Quality
     * Assurance,增加分类实例的size属性
     * ,包括la,ld,lt三类.但似乎这三类属性跟之前的属性或者extraction2中的一些属性重合度很高.
     * 值得注意的是,这个函数写的太烂了,跟之前的changed_LOC重合太多,但是由于创建这两个函数的时间维度不同,暂时保持这样.
     * 执行的部分依赖于之前sloc执行的部分.
     *
     * @throws SQLException
     */
    public void size(boolean excuteAll) throws SQLException {
        System.out.println("Update Size");
        if (curAttributes == null) {
            obtainCurAttributes();
        }
        if (!curAttributes.contains("la")) {
            sql = "alter table " + metaTableName + " add (la int,ld int,lt int)";
            stmt.executeUpdate(sql);
            curAttributes.add("la");
            curAttributes.add("ld");
            curAttributes.add("lt");
        }

        List<List<Integer>> executeList;
        if (excuteAll == true) {
            if (commit_file_inExtracion1 == null) {
                obtainCFidInExtraction1();
            }
            executeList = commit_file_inExtracion1;
        } else {
            executeList = commit_file_parts;
        }
        for (List<Integer> list : executeList) {
            sql = "select old_start_line,old_end_line,new_start_line,new_end_line,id from hunks where commit_id="
                    + list.get(0) + " and file_id=" + list.get(1);
            Map<Integer, int[]> map = new HashMap<>();
            int ldFileLevel = 0;
            int laFileLevel = 0;
            int ltFileLevel = 0;
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                int ldHunkLevel = 0;
                int laHunkLevel = 0;
                if (resultSet.getInt(1) != 0) {
                    ldHunkLevel = resultSet.getInt(2) - resultSet.getInt(1) + 1;
                }
                if (resultSet.getInt(3) != 0) {
                    laHunkLevel = resultSet.getInt(4) - resultSet.getInt(3) + 1;
                }
                int[] array = new int[2];
                array[0] = ldHunkLevel;
                array[1] = laHunkLevel;
                ldFileLevel += ldHunkLevel;
                laFileLevel += laHunkLevel;
                map.put(resultSet.getInt(5), array);
            }
            for (Integer hunk_id : map.keySet()) {
                sql = "UPDATE " + metaTableName + " SET ld=" + map.get(hunk_id)[0] + " ,la=" + map.get(hunk_id)[1] + " where " +
                        "hunk_id=" + hunk_id;
                stmt.executeUpdate(sql);
            }
            sql = "SELECT sloc FROM " + metaTableName + " where commit_id=" + list.get(0)
                    + " and file_id=" + list.get(1);
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                ltFileLevel = resultSet.getInt(1);
            }
            ltFileLevel = ltFileLevel - laFileLevel + ldFileLevel;
            if (ltFileLevel < 0) {
                System.out.println("lt<0!!!" + " commit_id=" + list.get(0) + "file_id=" + list.get(1));
            }
            sql = "UPDATE " + metaTableName + " SET lt=" + ltFileLevel
                    + " where commit_id=" + list.get(0) + " and file_id=" + list.get(1);
            stmt.executeUpdate(sql); // 这个信息，似乎在extraction2中的detal计算时已经包含了啊。
        }
    }

    /**
     * 根据给定的两个commitId,获取这两个commitId所对应的时间.并需保证firstCommit出现在secondCommit之前.
     *
     * @param firstCommit
     * @param secondCommit
     * @return 起始时间和结束时间组成的list
     * @throws SQLException
     */
    private List<String> getTimeRangeBetweenTwoCommit(int firstCommit,
                                                      int secondCommit) throws SQLException {
        List<String> res = new ArrayList<>();
        sql = "select commit_date from scmlog where id=" + firstCommit
                + " or id=" + secondCommit;
        resultSet = stmt.executeQuery(sql);
        String startTime = null;
        String endTime = null;
        while (resultSet.next()) {
            if (startTime == null) {
                startTime = resultSet.getString(1);
                continue;
            }
            if (endTime == null) {
                endTime = resultSet.getString(1);
            }
        }
        if (endTime == null) {
            endTime = startTime;
        }
        res.add(startTime);
        res.add(endTime);
        return res;
    }

    /**
     * 根据论文A Large-Scale Empirical Study Of Just-in-Time Quality
     * Assurance,增加分类实例的fix信息,该信息表明某次change是否fix了一个bug.由于fix
     * bug的change相对于增加新功能的change更容易引入缺陷(论文中说的),所以该信息也许对分类有帮助.
     *
     * @throws SQLException
     */
    public void purpose(boolean excuteAll) throws SQLException {
        System.out.println("Update purpose");
        if (curAttributes == null) {
            obtainCurAttributes();
        }
        if (!curAttributes.contains("fix")) {
            sql = "alter table " + metaTableName + " add fix tinyint(1) default 0";
            stmt.executeUpdate(sql);
            curAttributes.add("fix");
        }
        List<List<Integer>> executeList = null;
        if (excuteAll == true) {
            if (commit_file_inExtracion1 == null) {
                obtainCFidInExtraction1();
            }
            executeList = commit_file_inExtracion1;
        } else {
            executeList = commit_file_parts;
        }

        for (List<Integer> list : executeList) {
            sql = "UPDATE " + metaTableName + ",scmlog SET fix=is_bug_fix where " + metaTableName + ".commit_id=scmlog.id and"
                    + " " + metaTableName + ".commit_id=" + list.get(0);
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 根据git log信息来update实例的history信息,相比history1更加准确,但是时间也相对会长一些.
     * 由于时间复杂度相对较高,只提取指定范围内的history信息,不提取总体的信息.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    //
    public void history(String gitFile) throws IOException,
            InterruptedException {
        System.out.println("Update history With Python");
        String command = "python " + System.getProperty("user.dir")
                + "/src/scripts/history.py -d "
                + databaseName + " -s " + start + " -e " + end + " -g "
                + gitFile;
        System.out.println(command);
        Process pythonProcess = Runtime.getRuntime().exec(command);
        BufferedReader bReader = new BufferedReader(new InputStreamReader(
                pythonProcess.getInputStream()));
        String line;
        while ((line = bReader.readLine()) != null) {
            System.out.println(line);
        }
        BufferedReader eReader = new BufferedReader(new InputStreamReader(
                pythonProcess.getErrorStream()));
        while ((line = eReader.readLine()) != null)
            pythonProcess.waitFor();
    }

    /**
     * 获取文件的上一次change. 也在history.py中实现了,目前没有用了.
     *
     * @param curCommitId
     * @param curFileId
     * @return 上一次修改的commit_id.
     * @throws SQLException
     */
    private int getLastChangeOfFile(int curCommitId, int curFileId)
            throws SQLException {
        sql = "SELECT type from actions where commit_id=" + curCommitId
                + " and file_id=" + curFileId;
        resultSet = stmt.executeQuery(sql);
        String curType = null;
        while (resultSet.next()) {
            curType = resultSet.getString(1);
        }
        if (curType.equals("A") || curType.equals("C")) {
            return curCommitId;
        }
        sql = "SELECT MAX(extraction1.id) from extraction1 where id<(select id from extraction1 where commit_id="
                + curCommitId
                + " and file_id="
                + curFileId
                + ") and file_id="
                + curFileId;
        resultSet = stmt.executeQuery(sql);
        int lastId = 0;
        while (resultSet.next()) {
            lastId = resultSet.getInt(1);
        }
        sql = "SELECT commit_id from extraction1 where id=" + lastId;
        resultSet = stmt.executeQuery(sql);
        int lastCommitId = 0;
        while (resultSet.next()) {
            lastCommitId = resultSet.getInt(1);
        }
        return lastCommitId;
    }

    /**
     * 对于(commit_id,file_id)所对应的文件,返回该文件第一次出现,也就是该文件上次被add时的位置.默认为同一文件的file_id相同
     * .也在history.py中实现了.暂时没用了.
     *
     * @param commit_id
     * @param file_id
     * @return 该文件对应的第一次被加入时的commit_id.
     * @throws SQLException
     */
    private List<Integer> getFirstAppearOfFile(int commit_id, int file_id)
            throws SQLException {
        sql = "SELECT MIN(extraction1.id) from extraction1,actions where extraction1.id<=(select id from extraction1 where commit_id="
                + commit_id
                + " and file_id="
                + file_id
                + ") and extraction1.file_id="
                + file_id
                + " and extraction1.commit_id=actions.commit_id and extraction1.file_id=actions.file_id";
        resultSet = stmt.executeQuery(sql);
        int id = 0;
        while (resultSet.next()) {
            id = resultSet.getInt(1);
        }
        int firtAppearCommitIdOnCurBranch = 0;
        String firstTypeOnCurBranch = null;
        String fileName = null;
        // 文件删除后file_id会不会重新分配?
        sql = "select extraction1.commit_id,type,current_file_path from extraction1,actions where extraction1.id="
                + id
                + " and extraction1.commit_id=actions.commit_id and extraction1.file_id=actions.file_id";
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            firtAppearCommitIdOnCurBranch = resultSet.getInt(1);
            firstTypeOnCurBranch = resultSet.getString(2);
            fileName = resultSet.getString(3);
        }
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/"));
            fileName = "/" + fileName;
        }
        List<Integer> res = new ArrayList<>();
        if (firstTypeOnCurBranch.equals("A")
                || firstTypeOnCurBranch.equals("C")) {
            res.add(firtAppearCommitIdOnCurBranch);
            res.add(file_id);
            return res;
        }
        sql = "select MAX(extraction1.id) from extraction1,actions where extraction1.id<"
                + id
                + " and extraction1.commit_id=actions.commit_id and extraction1.file_id=actions.file_id"
                + " and current_file_path like \""
                + fileName
                + "\" and type='A'";
        resultSet = stmt.executeQuery(sql);
        int acturalId = 0;
        while (resultSet.next()) {
            acturalId = resultSet.getInt(1);
        }
        sql = "select commit_id,file_id from extraction1 where id=" + acturalId;
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            res.add(resultSet.getInt(1));
            res.add(resultSet.getInt(2));
        }
        return res;
    }

    /**
     * 根据已存在的extraction1表,获得commit_id,file_id对,
     * 否则总是根据commitIdPart就总得去考虑文件类型是不是java文件,是否为test文件,而这一步起始在initial函数中已经做过了.
     *
     * @throws SQLException
     */
    private void obtainCFidInExtraction1() throws SQLException {
        commit_file_inExtracion1 = new ArrayList<>();
        for (Integer integer : commit_parts) {
            sql = "select extraction1.commit_id,extraction1.file_id from extraction1,actions where extraction1.commit_id="
                    + integer
                    + " and extraction1.commit_id=actions.commit_id and extraction1.file_id=actions.file_id and type!='D'";
            resultSet = stmt.executeQuery(sql);
            while (resultSet.next()) {
                List<Integer> temp = new ArrayList<>();
                temp.add(resultSet.getInt(1));
                temp.add(resultSet.getInt(2));
                commit_file_inExtracion1.add(temp);
            }
        }

    }

    /**
     * 对外的接口,用于查看当前extraction1中指定范围(构造函数中指定)内的commit_id,file_id对.
     *
     * @return
     * @throws SQLException
     */
    public List<List<Integer>> getCommit_file_inExtracion1()
            throws SQLException {
        if (commit_file_inExtracion1 == null) {
            obtainCFidInExtraction1();
        }
        return commit_file_inExtracion1;
    }

    /**
     * 根据年份对change进行加权平均,以评估rexp.默认上次更改据现在不会超过9年.有点粗糙,比如,如果同年的一月和十二月的差距会算作零年,
     * 但是前年12月和后年一月会算作一年的差距.
     *
     * @param datesList 输入的做出change的年份list.其中最后一个元素为作者当前所做的change,也就是year为当前的标准(
     *                  最新的year).
     * @return
     */
    private float changeWeightedByYear(List<String> datesList) {
        if (datesList.size() == 1) {
            return 0;
        }
        int[] yearsToNow = new int[10];
        int cur = getYearFromCommitdateString(datesList
                .get(datesList.size() - 1));
        for (int i = datesList.size() - 2; i >= 0; i--) {
            int last = getYearFromCommitdateString(datesList.get(i));
            yearsToNow[cur - last]++;
        }
        float res = 0f;
        for (int i = 0; i < yearsToNow.length; i++) {
            res = res + (float) yearsToNow[i] / (i + 1);
        }
        return res;
    }

    /**
     * 根据数据库中的commit_date的字符串,获取commit_date的年份. 已在history.py中实现.暂时没用了.
     *
     * @param commit_date
     * @return
     */
    private int getYearFromCommitdateString(String commit_date) {
        return Integer.parseInt(commit_date.split(" ")[0].split("-")[0]);
    }

    @Override
    public Map<List<Integer>, StringBuffer> getContentMap() throws SQLException {
        Map<List<Integer>, StringBuffer> content = new LinkedHashMap<>();
        StringBuffer titleBuffer = new StringBuffer();
        sql = "select * from " + metaTableName + " where id=1";
        resultSet = stmt.executeQuery(sql);
        int colcount = resultSet.getMetaData().getColumnCount();
        int labelIndex = 0;
        for (int i = title.size() + 2; i <= colcount; i++) {
            String colName = resultSet.getMetaData().getColumnName(i);
            titleBuffer.append(colName + ",");
        }
        content.put(title, titleBuffer);

        for (List<Integer> commit_file_patch_offest : commit_file_patch_offset_part) {
            StringBuffer temp = new StringBuffer();
            sql = "select * from " + metaTableName + " where commit_id=" + commit_file_patch_offest.get(0) + " and file_id="
                    + commit_file_patch_offest.get(1) + " and patch_id=" + commit_file_patch_offest.get(2) + " and " +
                    "offset=" + commit_file_patch_offest.get(3);
            resultSet = stmt.executeQuery(sql);
            int colCount = resultSet.getMetaData().getColumnCount();
            resultSet.next();
            for (int i = title.size() + 2; i <= colCount; i++) {
                temp.append(resultSet.getString(i) + ",");
            }
            content.put(commit_file_patch_offest, temp);
        }
        return content;
    }

    public double countRatio() throws SQLException {
        int minId = Integer.MAX_VALUE;
        int maxId = Integer.MIN_VALUE;
        int total = 0;
        int bug = 0;
        sql = "select min(id) from " + metaTableName + " where commit_id=" + commit_parts.get(0);
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            minId = resultSet.getInt(1);
        }
        sql = "select max(id) from " + metaTableName + " where commit_id=" + commit_parts.get(commit_parts.size() - 1);
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            maxId = resultSet.getInt(1);
        }
        sql = "select count(*) from " + metaTableName + " where id>=" + minId + " and id<=" + maxId;
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            total = resultSet.getInt(1);
        }
        sql = "select count(*) from " + metaTableName + " where id>=" + minId + " and id<=" + maxId + " and bug_introducing=1";
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
            bug = resultSet.getInt(1);
        }
        return (double) bug / total;
    }
}

