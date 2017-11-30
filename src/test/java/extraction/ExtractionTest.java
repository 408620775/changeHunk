package extraction;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ExtractionTest {
    @Test
    public void parseHunkRangeTest() throws Exception {
        String hunkString = "@@ -340,14 +356,15 @@ public class StorageService extends AbstractService {\n" +
                "                     }\n" +
                " \n" +
                "                 }\n" +
                "+            }\n" +
                "             if(voldemortConfig.isServerRoutingEnabled() && !isSlop) {\n" +
                "-                this.storeRepository.removeRoutedStore(engineName);\n" +
                "+                this.storeRepository.removeRoutedStore(storeName);\n" +
                "                 for(Node node: metadata.getCluster().getNodes())\n" +
                "                     this.storeRepository.removeNodeStore(storeName, node.getId());\n" +
                "             }\n" +
                "         }\n" +
                " \n" +
                "-        storeRepository.removeStorageEngine(engineName);\n" +
                "+        storeRepository.removeStorageEngine(storeName);\n" +
                "         if(!isView)\n" +
                "             engine.truncate();\n" +
                "         engine.close();";
        int[] ranges = Extraction.parseHunkRange(hunkString);
        Assert.assertEquals(ranges[0],340);
        Assert.assertEquals(ranges[1],14);
        Assert.assertEquals(ranges[2],356);
        Assert.assertEquals(ranges[3],15);
    }

    @Test
    public void getHunksCache() throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta("MyVoldemort",501,800);
    }

}