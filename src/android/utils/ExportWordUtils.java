package cn.com.ths.exportdoc.thsexportdoc.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.com.ths.exportdoc.thsexportdoc.service.DocDynamicTable;
import cn.com.ths.exportdoc.thsexportdoc.service.DocImage;
import cn.com.ths.exportdoc.thsexportdoc.service.DocumentPage;
import cn.com.ths.exportdoc.thsexportdoc.service.RepDocTemplate;

public class ExportWordUtils {
   private static ExportWordUtils exportWordUtils;
   public  static ExportWordUtils getInstance(){
       if(exportWordUtils==null){
           exportWordUtils = new ExportWordUtils();
       }
       return exportWordUtils;
   }
    /**
     * 导出word
     * @param json 遵循规范格式传入，{"filename":"生成的文件名称","templatepath":"模版名称",
     *             singledata:{"key":"value","key1":"value1" ....},//存储替换模版文件中单个对象的数据
     *             imgdata:[{"key":"对应word标签key","paths":[{"path":"图片地址"}]}],//需要插入的图片数据
     *             tabledata:[{"key":"list","data":[{"key":"value","key1":"value1" ....}]}]}//存储动态表格数据
     *
     * }}
     */
    public void exportWord(String json, Activity ctx, CallbackContext callbackContext, CordovaInterface cordova) throws Exception {
        if (json == null || json.length() <= 0) {
            Log.e("异常", "数据为空不能生成word");
            callbackContext.error("数据为空不能生成word");
            return;
        }
        Map<String,Object> map = JsonUtil.json2map(json);
//        MSWordTool changer = new MSWordTool();
//        changer.setTemplate((String) map.get("templatepath"));
//        // 获取需要生成word的模版数据
//        Map<String,Object> data = (Map<String, Object>) map.get("data");
//        // 定义Word模板参数替换的map
//        List<Map<String,Object>> singledatas = (List<Map<String,Object>>) data.get("singledata");
//        // 替换单表数据
//        for (Map<String,Object> singledata : singledatas) {
//            changer.replaceText((Map<String, String>) singledata.get("data"), (String) singledata.get("key"));
//        }
//        //替换表格数据
//        List<Map<String,Object>> tabledatas = (List<Map<String, Object>>) data.get("tabledata");
//        for (Map<String,Object> tabledata:tabledatas) {
//            List<Map<String,Object>> datas = (List<Map<String, Object>>) tabledata.get("data");
//            if(datas.size()>0){
//                changer.fillTableAtBookMark((String)tabledata.get("key") ,datas);
//            }
//
//        }
        String tpath = cpTemplate((String) map.get("templatepath"),ctx);
        map.put("templatepath",tpath);
        DocumentPage page=new DocumentPage();
        // 获取需要生成word的模版数据
        // 定义Word模板参数替换的map
        Map<String,Object> paramDataMap = (Map<String, Object>) map.get("singledata");
        page.setParamMap(paramDataMap);




        //获取需要生成的图片数据
        List<Map<String,Object>> imags = (List<Map<String, Object>>) map.get("imgdata");
        if (imags != null) {
            for (Map<String,Object> imgdata : imags) {
                //封装文档体中需要插入的图片，集合中存放的是图片对象
                List<DocImage> imageList=new ArrayList<DocImage>();
                for (Map<String,Object> img : (List<Map<String, Object>>)imgdata.get("paths")) {
//                    String ipath = cpTemplate((String) img.get("path"),ctx);
//                    img.put("path",ipath);
                    //图片对象构造方法有多个重载，本例中使用的构造方法有3个参数，分别为图片路径，长，宽
                    DocImage docImage=new DocImage((String)img.get("path"),null,100.0);
                    imageList.add(docImage);
                }
                //将图片插入到标记点位置
                page.getImages().put((String)imgdata.get("key"), imageList);
            }
        }

        //定义Word模板动态表格替换的map
        Map<String,DocDynamicTable> paramDynamicTableMap=new HashMap<String,DocDynamicTable>();
        //添加数据
        List<Map<String,Object>> tabledatas = (List<Map<String, Object>>) map.get("tabledata");
        if(tabledatas!=null){
            for (Map<String,Object> tabledata:tabledatas) {
                List<Map<String,Object>> datas = (List<Map<String, Object>>) tabledata.get("data");
                if(datas != null){
                    // 设置word里面的动态表格
                    DocDynamicTable dynaicTable = new DocDynamicTable();
                    DocDynamicTable.InnerDocTable docTable = dynaicTable.getDocTable();
                    //设置动态数据的开始行，参数为数组，值为数据行的首行序号（从1开始），如果标题行存在折行，则数组值为多个
                    List<BigInteger> datanums = ((List) tabledata.get("startDataNums"));
                    int[] nums = new int[datanums.size()];
                    for (int i = 0;i<datanums.size();i++){
                        nums[i] = datanums.get(i).intValue();
                    }
                    docTable.setDataRowStarts(nums);
                    for (Map<String,Object> tdata:datas) {
                        docTable.addRow(tdata);
                    }
                    if(datas.size()>0){
                        paramDynamicTableMap.put((String)tabledata.get("key"), dynaicTable);
                    }
                }
            }
        }

        RepDocTemplate doc=new RepDocTemplate(ctx);
        //如果需要将word转为pdf,将下面参数false改为true
        try {

            doc.exportWord(page,paramDynamicTableMap,null, (String)map.get("filename"), (String) map.get("templatepath"),false,callbackContext);
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    callbackContext.success("OK");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }

    }
    public String cpTemplate(String templateName, Context ctx) {
        String path = getSDPath()+"/"+ctx.getPackageName()+"/" + templateName;
        File f = new File(path);
        //新增删除目录操作，防止修改模板不更新
        deleteDirectory(f.getParent());
        if (!f.exists()) {
            File pathDir=new File(f.getParent());
            if(!pathDir.exists()){
                pathDir.mkdirs();
            }
            try {
                f.createNewFile();
                FileOutputStream out = new FileOutputStream(path);
                InputStream in = ctx.getAssets().open("www/assets/template/"+templateName);
                byte[] buffer = new byte[1024];
                int readBytes = 0;
                while ((readBytes = in.read(buffer)) != -1)
                    out.write(buffer, 0, readBytes);
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return path;
    }
    public String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);//判断sd卡是否存在
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
        }
        return sdDir.toString();
    }
    /**
     * 删除目录
     */
    public  boolean deleteDirectory(String dir) {
        // 如果dir不以文件分隔符结尾，自动添加文件分隔符
        if (!dir.endsWith(File.separator))
            dir = dir + File.separator;
        File dirFile = new File(dir);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if ((!dirFile.exists()) || (!dirFile.isDirectory())) {
            System.out.println("删除目录失败：" + dir + "不存在！");
            return false;
        }
        // 用于标识是否删除成功
        boolean flag = true;
        // 删除文件夹中的所有文件包括子目录
        File[] files = dirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            // 删除子文件
            if (files[i].isFile()) {
                flag = delFile(files[i].getAbsolutePath());
                if (!flag)
                    break;
            }
            // 删除子目录
            else if (files[i].isDirectory()) {
                flag = deleteDirectory(files[i]
                        .getAbsolutePath());
                if (!flag)
                    break;
            }
        }
        if (!flag) {
            System.out.println("删除目录失败！");
            return false;
        }
        // 删除当前目录
        if (dirFile.delete()) {
            System.out.println("删除目录" + dir + "成功！");
            return true;
        } else {
            return false;
        }
    }
    public  boolean delFile(String path) {
        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                if (file.delete())
                {
                    return true;
                }
                else
                {
                    return false;
                }
            }
        }
        return true;
    }
}
