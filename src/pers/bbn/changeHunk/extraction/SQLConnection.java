package pers.bbn.changeHunk.extraction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * 数据库连接类. 1.通过构造函数选择需要连接的数据库并实现与数据库的连接 2.通过返回stmt,使调用SQLConnection的类能够执行sql语句
 *
 * @author niu
 *
 */
public class SQLConnection {
    Connection conn = null;
    Statement stmt = null;
    private String drivername, databasename, userName, password;
    String propName="database.properties";
    public SQLConnection(String baseName) {
        File propsfile = new File(propName);
        try {
            FileInputStream fis = new FileInputStream(propsfile);
            Properties prop = new Properties();
            prop.load(fis);
            drivername=prop.getProperty("Driver");
            databasename=prop.getProperty("URL")+baseName;
            userName=prop.getProperty("UserName");
            password=prop.getProperty("Password");
            connect();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void connect() {
        try {
            Class.forName(drivername);
            System.out.println("成功加载mysql驱动");
            conn = DriverManager.getConnection(databasename,userName,password);
            stmt = conn.createStatement();
        } catch (Exception e) {
            System.out.println("MySQL操作错误");
            e.printStackTrace();
        }
    }

    public Statement getStmt() {
        return stmt;
    }

    public String getDatabase() {
        return databasename;
    }
}
