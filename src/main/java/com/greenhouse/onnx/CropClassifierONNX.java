package com.greenhouse.onnx;

import ai.onnxruntime.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 农作物生长周期识别 - ONNX Runtime 推理核心
 *
 * 模型: YOLOv8n-cls, 320×320, 14类（棉花/草莓/向日葵的生长阶段）
 */
public class CropClassifierONNX implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final int imgWidth;
    private final int imgHeight;

    /** 14 类 - 必须与模型训练时的字母序一致 */
    private static final String[] CLASS_NAMES = {
        "cotton_flowering",      // 0: 棉花-开花期
        "cotton_fruiting",       // 1: 棉花-结果期
        "cotton_plant",          // 2: 棉花-成株期
        "cotton_seedling",       // 3: 棉花-幼苗期
        "cotton_sprout",         // 4: 棉花-发芽期
        "strawberry_flowering",  // 5: 草莓-开花期
        "strawberry_fruiting",   // 6: 草莓-结果期
        "strawberry_growing",    // 7: 草莓-生长期
        "strawberry_mature",     // 8: 草莓-成熟期
        "sunflower_earlyBloom",  // 9: 向日葵-早花期
        "sunflower_healthy",     // 10: 向日葵-健康期
        "sunflower_matureBud",   // 11: 向日葵-成熟花蕾期
        "sunflower_wilted",      // 12: 向日葵-枯萎期
        "sunflower_youngBud"     // 13: 向日葵-幼蕾期
    };

    private static final String[] CLASS_NAMES_CN = {
        "棉花-开花期", "棉花-结果期", "棉花-成株期", "棉花-幼苗期",
        "棉花-发芽期",
        "草莓-开花期", "草莓-结果期", "草莓-生长期", "草莓-成熟期",
        "向日葵-早花期", "向日葵-健康期", "向日葵-成熟花蕾期",
        "向日葵-枯萎期", "向日葵-幼蕾期"
    };

    /**
     * 从文件路径加载模型
     */
    public CropClassifierONNX(String modelPath, int imgWidth, int imgHeight) throws OrtException {
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = env.createSession(modelPath, opts);
    }

    /**
     * 从字节数组加载模型（适用于 classpath 资源 / JAR 内打包）
     */
    public CropClassifierONNX(byte[] modelBytes, int imgWidth, int imgHeight) throws OrtException {
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = env.createSession(modelBytes, opts);
    }

    // ==================== 预测接口 ====================

    /** 从文件路径预测 */
    public PredictionResult predict(String imagePath) throws Exception {
        BufferedImage img = ImageIO.read(new File(imagePath));
        if (img == null) throw new IllegalArgumentException("无法读取图片: " + imagePath);
        return predict(img);
    }

    /** 从 BufferedImage 预测 */
    public PredictionResult predict(BufferedImage image) throws Exception {
        float[] inputData = preprocess(image);
        float[] probs = runInference(inputData);
        return parseResult(probs);
    }

    /** 从字节数组预测（上传场景） */
    public PredictionResult predict(byte[] imageBytes) throws Exception {
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (img == null) throw new IllegalArgumentException("无法解析图片数据");
        return predict(img);
    }

    // ==================== 预处理 ====================

    private float[] preprocess(BufferedImage original) {
        // 1. 转为 BGR 色彩空间
        BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(),
                                              BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        // 2. 等比缩放 + 居中填充（letterbox）
        BufferedImage resized = letterboxResize(rgb, imgWidth, imgHeight);

        // 3. 提取像素 → CHW float 数组，归一化到 [0, 1]
        byte[] pixels = ((DataBufferByte) resized.getRaster().getDataBuffer()).getData();
        int h = imgHeight;
        int w = imgWidth;
        int channels = 3;

        float[] chw = new float[channels * h * w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int hwcIdx = (y * w + x) * channels;
                for (int c = 0; c < channels; c++) {
                    int rgbC = 2 - c; // BGR → RGB
                    int chwIdx = (rgbC * h + y) * w + x;
                    chw[chwIdx] = (pixels[hwcIdx + c] & 0xFF) / 255.0f;
                }
            }
        }
        return chw;
    }

    /** 等比缩放后居中填充，保证不拉伸变形 */
    private BufferedImage letterboxResize(BufferedImage src, int targetW, int targetH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        double scale = Math.min((double) targetW / srcW, (double) targetH / srcH);
        int newW = (int) (srcW * scale);
        int newH = (int) (srcH * scale);

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();

        // 灰色填充（114, 114, 114 — YOLOv8 默认）
        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);
        g = result.createGraphics();
        g.setColor(new Color(114, 114, 114));
        g.fillRect(0, 0, targetW, targetH);
        int xOffset = (targetW - newW) / 2;
        int yOffset = (targetH - newH) / 2;
        g.drawImage(scaled, xOffset, yOffset, null);
        g.dispose();

        return result;
    }

    // ==================== 推理 ====================

    private float[] runInference(float[] chwData) throws OrtException {
        long[] shape = {1, 3, imgHeight, imgWidth};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chwData), shape)) {
            Map<String, OnnxTensor> inputs = Collections.singletonMap("images", tensor);
            try (OrtSession.Result output = session.run(inputs)) {
                OnnxTensor outTensor = (OnnxTensor) output.get(0);
                float[][] result = (float[][]) outTensor.getValue();
                return result[0];
            }
        }
    }

    // ==================== 结果解析 ====================

    private PredictionResult parseResult(float[] probs) {
        int bestIdx = 0;
        float bestProb = probs[0];
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > bestProb) {
                bestProb = probs[i];
                bestIdx = i;
            }
        }

        // Top3
        int[] topIndices = new int[3];
        float[] topProbs = new float[3];
        float[] copy = probs.clone();
        for (int k = 0; k < 3; k++) {
            int maxI = 0;
            float maxV = copy[0];
            for (int i = 1; i < copy.length; i++) {
                if (copy[i] > maxV) { maxV = copy[i]; maxI = i; }
            }
            topIndices[k] = maxI;
            topProbs[k] = maxV;
            copy[maxI] = -1;
        }

        return new PredictionResult(bestIdx, CLASS_NAMES[bestIdx],
                                     CLASS_NAMES_CN[bestIdx],
                                     bestProb, probs, topIndices, topProbs);
    }

    @Override
    public void close() throws OrtException {
        session.close();
        env.close();
    }

    // ==================== 结果类 ====================

    public static class PredictionResult {
        public final int classId;
        public final String classNameEn;
        public final String classNameCn;
        public final float confidence;
        public final float[] allProbs;
        public final int[] top3Indices;
        public final float[] top3Probs;

        PredictionResult(int classId, String nameEn, String nameCn,
                        float confidence, float[] allProbs,
                        int[] top3Indices, float[] top3Probs) {
            this.classId = classId;
            this.classNameEn = nameEn;
            this.classNameCn = nameCn;
            this.confidence = confidence;
            this.allProbs = allProbs;
            this.top3Indices = top3Indices;
            this.top3Probs = top3Probs;
        }
    }

    public String[] getClassNames() { return CLASS_NAMES; }
    public String[] getClassNamesCn() { return CLASS_NAMES_CN; }
}
