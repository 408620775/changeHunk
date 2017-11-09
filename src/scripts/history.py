#!/usr/bin/python
# -*- coding: UTF-8 -*- 
import MySQLdb
import os
import sys
import datetime
import getopt


class extraction1:
    # 定义构造方法
    username = ''
    password = ''
    metaTableName = ''

    def __init__(self, database, startNum, endNum):
        self.readDatabaseConfig()
        self.conn = MySQLdb.connect(host="localhost", port=3306, user=self.username, passwd=self.password, db=database)
        self.cursor = self.conn.cursor()
        self.curAttributes = set()
        self.cursor.execute("select id from scmlog order by commit_date")
        row = self.cursor.fetchall()
        self.commit_ids = []
        self.commit_fileIdInExtraction1 = {}
        self.month = {'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6, 'Jul': 7, 'Aug': 8, 'Sep': 9,
                      'Oct': 10, 'Nov': 11, 'Dec': 12}
        for content in row[startNum - 1:endNum]:
            self.commit_ids.append(int(content[0]))
            # print commit_ids,len(commit_ids)

    def readDatabaseConfig(self):
        config = open('database.properties')
        line = config.readline()
        while line:
            if line.startswith("UserName"):
                self.username = line.split('=')[1].strip('\n')
            elif line.startswith('Password'):
                self.password = line.split('=')[1].strip('\n')
            elif line.startswith('MetaTableName'):
                self.metaTableName = line.split('=')[1].strip('\n')
            line = config.readline()

    def history(self, gitProject):
        self.cursor.execute("desc " + self.metaTableName)
        row = self.cursor.fetchall()
        for content in row:
            self.curAttributes.add(content[0])
        if 'NEDV' not in self.curAttributes:
            self.cursor.execute("ALTER TABLE " + self.metaTableName + " ADD (NEDV int,AGE long,NUC int)")
            self.conn.commit()
        if 'EXP' not in self.curAttributes:
            self.cursor.execute("ALTER TABLE " + self.metaTableName + " ADD (EXP int,REXP float,SEXP int)")
            self.conn.commit()
        self.commit_fileIdInExtraction1 = self.getCommitFileIdMap(self.commit_ids);
        tmpFile = os.path.split(os.path.realpath(sys.argv[0]))[0] + '/tmp.txt'
        f = open(tmpFile, 'w')  # 在脚本所在地创建临时文件
        os.chdir(gitProject)  # 进入git工程所在目录
        for key in self.commit_fileIdInExtraction1.keys():
            self.updateHistoryForCommit(key)

    def updateHistoryForCommit(self, key):
        print 'commitId:' + str(key)
        self.cursor.execute("select rev,commit_date,author_id from scmlog where id=" + str(key))
        row = self.cursor.fetchone()
        rev = row[0]
        commit_date = row[1]
        author_id = row[2]
        os.system('git reset --hard ' + rev)
        for content in self.commit_fileIdInExtraction1[key]:
            file_id = content[0]
            file_name = content[1]
            print 'file_id:', file_id
            tmpFile = 'tmp.txt'
            os.system('git whatchanged ' + file_name + ' >' + tmpFile)
            (nedv, age, nuc, rexp) = self.dealWithGitLog(tmpFile)
            self.cursor.execute(
                "select current_file_path from " + self.metaTableName + ",scmlog,actions where " + self.metaTableName
                + ".commit_id=scmlog.id and author_id=" + str(author_id) + " and commit_date<'" + str(commit_date)
                + "' and " + self.metaTableName + ".commit_id=actions.commit_id and " + self.metaTableName + ".file_id=actions.file_id")
            row = self.cursor.fetchall()
            exp = 0
            sexp = 0
            if '/' in file_name:
                subSystem = file_name.split('/')[0]
            for res in row:
                exp = exp + 1
                if res[0].startswith(subSystem):
                    sexp = sexp + 1
            finalOrder = 'update ' + self.metaTableName + ' set NEDV=' + str(nedv) + ',AGE=' + str(age) + ',NUC=' + str(
                nuc) + ',EXP=' + str(exp) + ',REXP=' + str(rexp) + ',SEXP=' + str(sexp) + ' where commit_id=' + str(
                key) + ' and file_id=' + str(file_id)
            print finalOrder
            self.cursor.execute(finalOrder)
            self.conn.commit()

    def getCommitFileIdMap(self, commit_ids):
        myDict = {}
        count = 0
        for commit_id in commit_ids:
            self.cursor.execute(
                "select " + self.metaTableName + ".file_id,current_file_path from " + self.metaTableName + ","
                "actions where "+ self.metaTableName + ".commit_id=" + str(commit_id) + " and " + self.metaTableName
                + ".file_id=actions.file_id and " + self.metaTableName + ".commit_id=actions.commit_id")
            row = self.cursor.fetchall()
            if row:
                if commit_id not in myDict.keys():
                    myDict[commit_id] = []
                for res in row:
                    count = count + 1
                    tmp = [res[0], res[1]]
                    myDict[commit_id].append(tmp)

        print myDict
        print "the num of total commit is " + str(len(myDict)) + " and the total file is " + str(count)
        return myDict

    def __del__(self):
        self.cursor.close()
        self.conn.close()

    def dealWithGitLog(self, logFile):
        f = open(logFile)
        count = 0;
        authors = set()
        age = 0
        curDateTime = 0
        lastDateTime = 0
        line = f.readline()
        curAuthor = ''
        exp = 0
        rexp = {}
        while line:
            if line.startswith('commit'):
                curRev = line.split()[1]
            if line.startswith('Author'):
                count = count + 1
                author = line.split(':')[1].split('<')[0].strip()
                authors.add(author)
                if curAuthor == '':
                    curAuthor = author
                    line = f.readline()
                    curDateTime = self.strToDateTime(line)
                elif author == curAuthor:
                    exp = exp + 1
                    line = f.readline()
                    lastDateTimeCur = self.strToDateTime(line)
                    if lastDateTime == 0:
                        lastDateTime = lastDateTimeCur
                    key = (curDateTime - lastDateTimeCur).days / 365
                    if key == -1:
                        key = 0
                    if rexp.has_key(key + 1):
                        rexp[key + 1] = rexp[key + 1] + 1
                    else:
                        rexp[key + 1] = 1
                else:
                    if lastDateTime == 0:
                        line = f.readline()
                        lastDateTime = self.strToDateTime(line)
            line = f.readline()
        if lastDateTime == 0:
            lastDateTime = curDateTime
        rexpValue = 0.0
        print 'rexp', rexp
        if len(rexp) != 0:
            for key in rexp:
                rexpValue = rexpValue + float(rexp[key]) / key

        return len(authors), (curDateTime - lastDateTime).seconds, count, rexpValue

    def strToDateTime(self, line):
        array = line.split()
        time = str(self.month[array[2]]) + ' ' + array[3] + ' ' + array[4] + ' ' + array[5]
        covertTime = datetime.datetime.strptime(time, '%m %d %H:%M:%S %Y')
        return covertTime


def usage():
    print """
Obtain history infomation for the specified data range, and the result will be saved in the metaTableName table in the 
database which miningit obtain.

Options:

  -h, --help                     print this usage message.
  -d,--database                  the database which will save the result.
  -s, --start                    start commit_id of the  date range.
  -e, --end                      end commit_id of the data range.
  -g, --gitfile                  the git project which need to obtain the history information.
"""


def execute(argv, short_opts, long_opts):
    opts, args = getopt.getopt(argv, short_opts, long_opts)
    database = ''
    start = 0
    end = 0
    gitfile = ''
    for op, value in opts:
        if op in ("-d", "--database"):
            database = value
        elif op in ("-s", "--start"):
            start = value
        elif op in ("-e", "--end"):
            end = value
        elif op in ("-g", "--gitfile"):
            gitfile = value
        elif op in ("-h", "--help"):
            usage()
            return
    print database, start, end, gitfile
    if database and start and end and gitfile:
        e = extraction1(database, int(start), int(end))
        e.history(gitfile)
    else:
        print 'Parameter does not meet the requirements.'


if __name__ == '__main__':
    short_opts = "hd:s:e:g:"
    long_opts = ["help", "database", "start", "end", "gitfile"]
    argv = sys.argv[1:]
    execute(argv, short_opts, long_opts)
    tmpFile = 'tmp.txt'
    if os.path.exists(tmpFile):
        os.remove(tmpFile)
