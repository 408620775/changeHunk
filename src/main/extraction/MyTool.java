package src.main.extraction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 工具类,用于处理一些简单的数学运算,禁止实例化.
 *
 * @author niu
 *
 */
public class MyTool {
    private MyTool() {

    }

    /**
     * 根据给定的一些列数据,计算该list的熵.
     *
     * @param changeOfFile
     * @return
     */
    public static float calEntropy(List<Integer> changeOfFile)
            throws IllegalArgumentException {
        float sum = 0f;
        for (Integer integer : changeOfFile) {
            // if (integer<0) {
            // System.out.println("概率值不能为小于等于0!");
            // throw new IllegalArgumentException();
            // }else if (integer==0) {
            // System.out.println("abnormal point");
            // }
            sum += integer;
        }
        if (sum - 0 < 0.001) {
            System.out.println("sum is 0!");
            return 0f;
        }
        float entropy = 0f;
        for (Integer integer : changeOfFile) {
            if (integer == 0) {
                continue;
            }
            float ratio = integer / sum;
            entropy -= ratio * (Math.log(ratio) / Math.log(2));
        }
        return entropy;
    }

    /**
     * 格式化打印数据库数据,每个条目是一个list.
     *
     */
    public static void printDBdata(List<List<String>> datas) {
        if (datas == null) {
            return;
        }
        int[] lengths = new int[datas.get(0).size()];
        for (List<String> data : datas) {
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).length() > lengths[i]) {
                    lengths[i] = data.get(i).length();
                }
            }
        }
        for (List<String> data : datas) {
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).length() <= lengths[i]) {
                    String tmp = data.get(i);
                    for (int j = 0; j < lengths[i] - data.get(i).length(); j++) {
                        tmp = tmp + " ";
                    }
                    System.out.print(tmp + "  ");
                    System.out.print("|  ");
                }
            }
            System.out.println();
        }
    }

    /**
     * 格式化打印运行结果.
     *
     * @param res
     */
    public static void printRes(Map<List<String>, List<Double>> res) {
        DecimalFormat df = new DecimalFormat("0.00");
        System.out.printf("%-30s", "  ");
        System.out.printf("%-16s", "Method");
        List<String> measures = Arrays.asList("C-Rec", "B-Rec", "C-Pre",
                "B-Pre", "C-Fme", "B-Fme", "Auc", "G-mean", "Acc");
        for (String string : measures) {
            System.out.printf("%-8s", string);
        }
        System.out.println();
        for (List<String> m : res.keySet()) {
            System.out.printf("%-30s", m.get(0));
            System.out.printf("%-16s", m.get(1));
            for (Double value : res.get(m)) {
                System.out.printf("%-8s", String.valueOf(df.format(value)));
            }
            System.out.println();
        }
    }

    /**
     * 将运行得到的结果存储到csv文件中.
     * @param resMap 包含实验结果的map
     * @param saverFile 保存信息的文件.
     * @throws IOException
     */
    public static void saveRes(Map<List<String>, List<Double>> resMap,
                               File saverFile) throws IOException {
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(saverFile));
        DecimalFormat df = new DecimalFormat("0.00");
        bWriter.append("classifier,Method,C-Rec,B-Rec,C-Pre,B-Pre,C-Fme,B-Fme,Auc,G-mean,Acc\n");
        for (List<String> key : resMap.keySet()) {
            for (String string2 : key) {
                bWriter.append(string2 + ",");
            }
            for (double dou : resMap.get(key)) {
                bWriter.append(df.format(dou) + ",");
            }
            bWriter.append("\n");
        }
        bWriter.flush();
        bWriter.close();
    }
}
