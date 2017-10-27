package src.main.extraction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文件操作类，主要用于将得到的数据写入csv文件。
 *
 * @author niu
 *
 */
public class FileOperation {

    /**
     * 没必要实例化.
     */
    private FileOperation(){

    }
    /**
     * 写字典.
     *
     * @param dict
     *            字典名称
     * @param dictionary
     *            字典内容
     * @throws IOException
     */
    public static void writeDict(String dict, Map<String, String> dictionary)
            throws IOException {
        File di = new File(dict);
        BufferedWriter br = new BufferedWriter(new FileWriter(di));
        for (String string : dictionary.keySet()) {
            br.write(string + "   " + dictionary.get(string) + "\n");
        }
        br.flush();
        br.close();
    }

    /**
     * 将StringBuffer格式的内容写入指定文件中.
     * @param sBuffer
     * @param outFile
     * @throws IOException
     */
    public static void writeStringBuffer(StringBuffer sBuffer, String outFile)
            throws IOException {
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(new File(
                outFile)));
        bWriter.append(sBuffer);
        bWriter.flush();
        bWriter.close();
    }

    /**
     * 将常见的以map形式出现的各表内容写入到指定文件中.
     * @param map
     * @param outFile
     * @throws IOException
     */
    public static void writeContentMap(Map<List<Integer>, StringBuffer> map,String outFile) throws IOException {
        if (map == null) {
            System.out.println("Error, the contentMap is null!");
            return;
        }
        StringBuffer resBuffer = new StringBuffer();
        for (List<Integer> key : map.keySet()) {
            if (key.get(0)==-1) {
                resBuffer.append("commit_id").append(",").append("file_id")
                        .append(",").append(map.get(key)).append("\n");
            }else {
                resBuffer.append(key.get(0)).append(",").append(key.get(1))
                        .append(",").append(map.get(key)).append("\n");
            }
        }
        writeStringBuffer(resBuffer, outFile);
    }
}
