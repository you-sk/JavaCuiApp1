package com.cuiapp1.batch;

import java.io.File;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import net.arnx.jsonic.JSON;

public class app {

    /**
     * @param args the command line arguments
     * @throws java.net.MalformedURLException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws MalformedURLException, InterruptedException {

        //./batch.propetiesを取得
        URLClassLoader urlLoader = new URLClassLoader(new URL[]{new File(".").toURI().toURL()});
        ResourceBundle bundle = ResourceBundle.getBundle("batch", Locale.getDefault(), urlLoader);

        String[] dailyBatchIds = {"Bat011", "Bat012", "Bat013", "Bat014", "Bat015"};
        for (String dailyBatchId : dailyBatchIds) {
            System.err.println("バッチ実行：" + dailyBatchId);
            batchExec(bundle,dailyBatchId);
        }

        //月次実行判定→月次処理実行
        System.err.println("月次判定：Bat016");        
        if(monthlyCheck(bundle)){
            System.err.println("→月次処理を実行します");
            String[] monthlyBatchIds = {"Bat111", "Bat112", "Bat113", "Bat114"};
            for (String monthlyBatchId : monthlyBatchIds) {
                System.err.println("バッチ実行：" + monthlyBatchId);
                batchExec(bundle,monthlyBatchId);
            }            
        } else {
            System.err.println("→月次処理は実行されません");
        }

        //日次繰越処理[Bat016]
        System.err.println("バッチ実行：Bat016");
        batchExec(bundle,"Bat016");
    }

    //月次判定
    private static boolean monthlyCheck(ResourceBundle bundle){

        MyParams mp = new MyParams(bundle);
        
        Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(mp.getUrl()).path("Entity/cdekbnmp");
        final Invocation.Builder builder = target.request();
        //計上日データ
        final Form form = mp.getForm();
        form.param("CDCDEKBN", "KJODAT");
        form.param("CDKEY01", "KEIJOUBI");
        form.param("CDKEY02", "*");
        
        Response response = builder.post(Entity.form(form), Response.class);
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                String json = response.readEntity(String.class);

//                System.err.println(json);
                Map map = (Map) JSON.decode(json);
                String Cdstdt02 = map.get("Cdstdt02").toString();
                String Cdstdt04 = map.get("Cdstdt04").toString();
                System.err.println("…Equal? " + Cdstdt02 + ":" + Cdstdt04);
                return !Cdstdt02.equals(Cdstdt04);
        } else {
            System.err.println("…APIアクセス失敗！");
            System.err.println(response.getStatus());
            System.err.println(response.getEntity());
            return false;
        }
    }

    //バッチ処理実行    
    private static void batchExec(ResourceBundle bundle,String dailyBatchId) throws InterruptedException{
  
            MyParams mp = new MyParams(bundle);

            Client client = ClientBuilder.newClient();
            final WebTarget target = client.target(mp.getUrl()).path("Batch");
            final Invocation.Builder builder = target.request();

            final Form form = mp.getForm();
            form.param("batchId", dailyBatchId);

            Response response = builder.post(Entity.form(form), Response.class);

            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                String json = response.readEntity(String.class);

//                System.err.println(json);
                Map map = (Map) JSON.decode(json);

                final WebTarget target2 = client.target(mp.getUrl()).path("Entity/stbcellp");
                final Invocation.Builder builder2 = target2.request();

                final Form form2 = mp.getForm();
                form2.param("logyym", ((BigDecimal) map.get("logyym")).toString());
                form2.param("lognbr", ((BigDecimal) map.get("lognbr")).toString());
                
                int ngCount = 0;

                for (int i = 0; i < mp.getWaitCount(); i++) {

                    Thread.sleep(mp.getWaitTime());

                    Response response2 = builder2.post(Entity.form(form2), Response.class);

                    if (response2.getStatus() == Response.Status.OK.getStatusCode()) {
                        ngCount = 0;
                        String json2 = response2.readEntity(String.class);
                        Map map2 = (Map) JSON.decode(json2);
//                        System.err.println(json2);

                        String sbenddtt = (String) map2.get("Sbenddtt");

                        if (!"".equals(sbenddtt)) {
                            System.err.println("→" + (String) map2.get("Sbsrires"));
                            break;
                        } else {
                            System.err.println("…(実行中) 待機:" + (i+1) + "回目");
                        }
                    } else {
                        System.err.println("…APIアクセス失敗！");
                        System.err.println(response2.getStatus());
                        System.err.println(response2.getEntity());
                        ngCount++;
                        if(ngCount>2){ break; } //2回連続で200以外のコードが返ったら終わらせる
                    }
                }
            } else {
                System.err.println("…APIアクセス失敗！");
                System.err.println(response.getStatus());
                System.err.println(response.getEntity());
            }
    }
    
    //プロパティ情報保持クラス
    private static class MyParams{
        private final Form paramform;
        private final String url;
        private final Long waitTime;
        private final Integer waitCount;
        //コンストラクタ
        public MyParams() {
            paramform = new Form();
            waitTime = 5000l;
            waitCount = 120;
            url = "http://xxxx";
        }
        //コンストラクタ（こちらを使うこと)
        public MyParams(ResourceBundle bundle) {
            paramform = new Form();
            paramform.param("userid", bundle.getString("userid"));
            paramform.param("password", bundle.getString("password"));
            waitTime = Long.parseLong(bundle.getString("waitTime"));
            waitCount = Integer.parseInt(bundle.getString("waitCount"));
            url = bundle.getString("batchurl");  
        }
        //プロパティ
        public Form getForm(){ return paramform; }
        public Long getWaitTime(){ return waitTime; }
        public Integer getWaitCount(){ return waitCount; }
        public String getUrl(){ return url; }
    }

}
