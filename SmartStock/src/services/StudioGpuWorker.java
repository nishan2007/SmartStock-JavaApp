package services;

import ai.onnxruntime.*;
import com.sun.jna.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Map;

/** Isolated DirectML process. Binary stdin/stdout carries only bounded RGB tiles. */
public final class StudioGpuWorker {
    public static void main(String[] args) throws Exception {
        boolean removal=args.length>3&&args[3].equals("REMOVAL");
        System.load(args[1]); // DirectML dependency before ONNX Runtime is initialized.
        var env=OrtEnvironment.getEnvironment();
        try(var options=new OrtSession.SessionOptions()) {
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            options.setMemoryPatternOptimization(false);
            if(removal){
                // Large fused BiRefNet partitions exhaust integrated-GPU buffers.
                // Keep operators on DirectML but bound allocation to individual ops.
                options.addConfigEntry("ep.dml.disable_graph_fusion","1");
            }
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);
            // Maven's JNI is built without USE_DML. The pinned C runtime exports this factory.
            var handle=options.getClass().getDeclaredField("nativeHandle");handle.setAccessible(true);
            var dll=NativeLibrary.getInstance(args[2]);
            Pointer status=dll.getFunction("OrtSessionOptionsAppendExecutionProvider_DML").invokePointer(
                    new Object[]{new Pointer(handle.getLong(options)),0});
            if(status!=null)throw new IOException("DirectML device initialization failed");
            try(var session=env.createSession(args[0],options);
                var input=new DataInputStream(new BufferedInputStream(System.in));
                var output=new DataOutputStream(new BufferedOutputStream(System.out))) {
                output.writeInt(0x53534731);output.flush();
                while(true){
                    int w;try{w=input.readInt();}catch(EOFException ex){break;}
                    int h=input.readInt();if(removal?(w!=1024||h!=1024):(w<1||h<1||w>240||h>240))throw new IOException("Invalid input dimensions");
                    float[] rgb=new float[w*h*3];for(int i=0;i<rgb.length;i++)rgb[i]=input.readFloat();
                    try(var tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),new long[]{1,3,h,w});
                        var result=session.run(Map.of(removal?session.getInputNames().iterator().next():"input",tensor),java.util.Set.of(removal?session.getOutputNames().iterator().next():"output"))){
                        var out=(OnnxTensor)result.get(removal?session.getOutputNames().iterator().next():"output").orElseThrow();
                        long[] shape=out.getInfo().getShape();
                        if(removal){long count=1;for(long dimension:shape)count=Math.multiplyExact(count,dimension);if(count!=w*(long)h)throw new IOException("Invalid removal mask shape");}
                        else if(!java.util.Arrays.equals(shape,new long[]{1,3,h*4L,w*4L}))throw new IOException("Invalid output shape");
                        FloatBuffer values=out.getFloatBuffer();output.writeInt(values.remaining());
                        while(values.hasRemaining())output.writeFloat(values.get());output.flush();
                    }
                }
            }
        }
    }
}
