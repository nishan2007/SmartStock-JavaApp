package services;

import ai.onnxruntime.*;
import com.sun.jna.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Map;

/** Isolated DirectML process. Binary stdin/stdout carries only bounded RGB tiles. */
public final class StudioGpuWorker {
    public static void main(String[] args) throws Exception {
        System.load(args[1]); // DirectML dependency before ONNX Runtime is initialized.
        var env=OrtEnvironment.getEnvironment();
        try(var options=new OrtSession.SessionOptions()) {
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            options.setMemoryPatternOptimization(false);
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
                    int h=input.readInt();if(w<1||h<1||w>240||h>240)throw new IOException("Invalid tile");
                    float[] rgb=new float[w*h*3];for(int i=0;i<rgb.length;i++)rgb[i]=input.readFloat();
                    try(var tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),new long[]{1,3,h,w});
                        var result=session.run(Map.of("input",tensor))){
                        var out=(OnnxTensor)result.get("output").orElseThrow();
                        long[] shape=out.getInfo().getShape();
                        if(!java.util.Arrays.equals(shape,new long[]{1,3,h*4L,w*4L}))throw new IOException("Invalid output shape");
                        FloatBuffer values=out.getFloatBuffer();output.writeInt(values.remaining());
                        while(values.hasRemaining())output.writeFloat(values.get());output.flush();
                    }
                }
            }
        }
    }
}
