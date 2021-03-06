package com.neptune.bolt.facerig;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import com.google.gson.Gson;
import com.neptune.config.analyze.CaculateInfo;
import com.neptune.config.facerig.PictureKey;
import com.neptune.util.HDFSHelper;
import com.neptune.util.ImageHelper;
import com.neptune.util.LogWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Created by neptune on 16-9-13.
 * 预处理图片并将消息还原为对象的bolt
 */
public class PretreatBolt extends BaseRichBolt {
    private static final String TAG = "pretreat-bolt";
    private String logPath;

    private OutputCollector collector;
    private TopologyContext context;
    private int id;

    private int height = 227;
    private int width = 227;
    private HDFSHelper mHelper;

    public PretreatBolt(int height, int width, String logPath) {
        this.height = height;
        this.width = width;
        this.logPath = logPath;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;
        context = topologyContext;
        id = context.getThisTaskId();
        mHelper = new HDFSHelper(null);
        LogWriter.writeLog(logPath, TAG + "@" + id + ": prepared!");
    }

    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getString(0);
        Gson gson = new Gson();
        PictureKey key = gson.fromJson(json, PictureKey.class);

        if (key != null) {
            //下载图片
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedImage img = null;
            if (key.url != null && mHelper.download(baos, key.url)) {
                InputStream in = new ByteArrayInputStream(baos.toByteArray());
                try {
                    img = ImageIO.read(in);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (img != null) {
                if (img.getHeight() != height || img.getWidth() != width) {
                    //裁剪图片
                    img = ImageHelper.resize(img, width, height);
                    //编码发送，采用与鉴黄项目相同的接口
                    byte[] value = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
                    CaculateInfo cal = new CaculateInfo(key.url, value, width, height, key.time_stamp);
                    collector.emit(new Values(cal, key));
                    LogWriter.writeLog(logPath, TAG + "@" + id + ": Reduce command :" + json);
                } else
                    LogWriter.writeLog(logPath, TAG + "@" + id + ": Fail to decode");
            }
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("CaculateInfo", "PictureKey"));
    }
}