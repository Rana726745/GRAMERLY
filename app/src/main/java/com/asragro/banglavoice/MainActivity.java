package com.asragro.banglavoice;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,17,31));
        getWindow().setNavigationBarColor(Color.rgb(7,17,31));

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new InvoiceBridge(), "AndroidInvoice");

        String html;
        try {
            StringBuilder b64 = new StringBuilder();
            for (int i=1;i<=4;i++) b64.append(readAsset("app_"+i+".txt"));
            html = ungzipBase64(b64.toString());
        } catch (Exception e) {
            html = "<html><body style='background:#07111f;color:white;font-family:sans-serif;padding:24px'><h2>AsR Egg Business</h2><p>Unable to load app interface.</p></body></html>";
        }
        webView.loadDataWithBaseURL("https://asr.local/", html, "text/html", "UTF-8", null);
    }

    private String readAsset(String name) throws Exception {
        InputStream in=getAssets().open(name);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1) out.write(buf,0,n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name()).trim();
    }

    private String ungzipBase64(String input) throws Exception {
        byte[] compressed=Base64.decode(input,Base64.DEFAULT);
        GZIPInputStream gis=new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192]; int n;
        while((n=gis.read(buffer))>0) bos.write(buffer,0,n);
        gis.close();
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private class InvoiceBridge {
        @JavascriptInterface public void saveInvoice(String json) {
            new Thread(() -> {
                try {
                    JSONObject o=new JSONObject(json);
                    String invoiceNo=cleanFileName(o.optString("invoiceNo","Invoice"));
                    String fileName=invoiceNo+".pdf";
                    PdfDocument doc=new PdfDocument();
                    PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();
                    PdfDocument.Page page=doc.startPage(info);
                    drawInvoice(page.getCanvas(),o);
                    doc.finishPage(page);
                    String saved=writePdf(doc,fileName);
                    doc.close();
                    notifyJs("PDF invoice saved: "+saved);
                } catch(Exception e) {
                    notifyJs("Invoice download failed");
                }
            }).start();
        }
    }

    private String writePdf(PdfDocument doc,String fileName) throws Exception {
        OutputStream out; String location;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
            ContentValues v=new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);
            v.put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AsR Egg Business");
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null) throw new Exception("create failed");
            out=getContentResolver().openOutputStream(uri);
            if(out==null) throw new Exception("open failed");
            location="Downloads/AsR Egg Business/"+fileName;
        } else {
            File base=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if(base==null) base=getFilesDir();
            File dir=new File(base,"AsR Egg Business");
            if(!dir.exists()&&!dir.mkdirs()) throw new Exception("folder failed");
            File file=new File(dir,fileName);
            out=new FileOutputStream(file);
            location=file.getAbsolutePath();
        }
        doc.writeTo(out); out.flush(); out.close(); return location;
    }

    private void drawInvoice(Canvas c,JSONObject o){
        final int W=595;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));

        p.setColor(Color.rgb(10,30,51)); c.drawRect(0,0,W,138,p);
        p.setColor(Color.rgb(255,183,3)); c.drawRect(0,132,W,138,p);

        p.setColor(Color.WHITE); p.setTextSize(22);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        c.drawText(shorten(o.optString("businessName","AsR Egg Business"),36),34,43,p);
        p.setTextSize(10); p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        c.drawText(shorten(o.optString("businessMobile",""),52),34,66,p);
        c.drawText(shorten(o.optString("businessAddress",""),52),34,84,p);

        p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(24);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        c.drawText("INVOICE",560,44,p);
        p.setTextSize(11); p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        c.drawText(shorten(o.optString("invoiceNo",""),26),560,66,p);
        c.drawText(shorten(o.optString("dateLabel",o.optString("date","")),30),560,84,p);
        p.setTextAlign(Paint.Align.LEFT);

        int y=172;
        p.setColor(Color.rgb(241,245,249)); c.drawRoundRect(34,y,561,y+82,12,12,p);
        p.setColor(Color.rgb(23,35,52)); p.setTextSize(10);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        c.drawText("BILL TO",50,y+22,p);
        p.setTextSize(15);
        c.drawText(shorten(o.optString("customerName","Cash Customer"),34),50,y+45,p);
        p.setTextSize(10); p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        c.drawText(shorten(o.optString("customerPhone",""),38),50,y+63,p);
        c.drawText(shorten(o.optString("customerAddress",""),42),300,y+45,p);

        y=285;
        p.setColor(Color.rgb(10,30,51)); c.drawRoundRect(34,y,561,y+34,8,8,p);
        p.setColor(Color.WHITE); p.setTextSize(10);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        c.drawText("ITEM",48,y+22,p); c.drawText("QTY",250,y+22,p); c.drawText("RATE / PC",360,y+22,p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText("AMOUNT",545,y+22,p); p.setTextAlign(Paint.Align.LEFT);

        y+=54; p.setColor(Color.rgb(23,35,52)); p.setTextSize(12);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        c.drawText(shorten(o.optString("category",""),25),48,y,p);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        String qt=trimNumber(o.optDouble("quantity",0))+" "+o.optString("unitLabel","");
        c.drawText(shorten(qt,18),250,y,p); c.drawText(moneyText(o.optDouble("unitPrice",0)),360,y,p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(moneyText(o.optDouble("subtotal",0)),545,y,p); p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.rgb(110,125,145)); p.setTextSize(9);
        c.drawText(trimNumber(o.optDouble("pieces",0))+" pcs",250,y+16,p);

        y=430; totalRow(c,p,y,"Subtotal",o.optDouble("subtotal",0),false);
        y+=27; totalRow(c,p,y,"Discount",-o.optDouble("discount",0),false);
        y+=35; p.setColor(Color.rgb(255,183,3)); c.drawRect(320,y-20,548,y-17,p);
        totalRow(c,p,y+4,"TOTAL",o.optDouble("total",0),true);
        y+=38; totalRow(c,p,y,"Paid",o.optDouble("paid",0),false);
        y+=27; totalRow(c,p,y,"Due",o.optDouble("due",0),false);

        String note=o.optString("note","");
        if(!note.isEmpty()){
            y+=44; p.setColor(Color.rgb(241,245,249)); c.drawRoundRect(34,y-20,561,y+42,10,10,p);
            p.setColor(Color.rgb(23,35,52)); p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
            p.setTextSize(10); c.drawText("NOTE",48,y,p);
            p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
            c.drawText(shorten(note,76),48,y+22,p);
        }

        p.setColor(Color.rgb(10,30,51)); c.drawRect(0,790,W,842,p);
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(10);
        c.drawText("Thank you for your business",W/2f,812,p);
        p.setTextSize(8); p.setColor(Color.rgb(186,199,213));
        c.drawText("Digital invoice generated by AsR Egg Business",W/2f,828,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void totalRow(Canvas c,Paint p,int y,String label,double value,boolean big){
        p.setColor(Color.rgb(23,35,52));
        p.setTypeface(android.graphics.Typeface.create("sans",big?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));
        p.setTextSize(big?16:11); c.drawText(label,330,y,p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(moneyText(value),545,y,p); p.setTextAlign(Paint.Align.LEFT);
    }

    private String moneyText(double v){ return (v<0?"- ":"")+"Tk "+trimNumber(Math.abs(v)); }
    private String trimNumber(double v){
        if(Math.abs(v-Math.rint(v))<0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(java.util.Locale.US,"%.2f",v);
    }
    private String shorten(String s,int max){
        if(s==null) return "";
        s=s.replace("\n"," ").replace("\r"," ").trim();
        return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";
    }
    private String cleanFileName(String s){
        String x=s==null?"Invoice":s.replaceAll("[^A-Za-z0-9._-]","_");
        return x.isEmpty()?"Invoice":x;
    }
    private void notifyJs(String message){
        runOnUiThread(() -> {
            if(webView!=null) webView.evaluateJavascript("toast("+JSONObject.quote(message)+")",null);
        });
    }

    @Override public void onBackPressed(){
        if(webView!=null&&webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(webView!=null){ webView.removeJavascriptInterface("AndroidInvoice"); webView.destroy(); }
        super.onDestroy();
    }
}
