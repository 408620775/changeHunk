package extraction;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class BowTest {
    @Test
    public void bowP() throws Exception {

        String sBuffer = new String("   hello world + hello world = 2 hello world");
        Map<String,Integer> map = Bow.bowP(sBuffer);
        for (String s : map.keySet()) {
            System.out.println(s+":"+map.get(s));
        }
    }

}