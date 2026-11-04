/* RunKK: deform a SINGLE assembled body texture. No independent limb sprites. */
(() => {
  'use strict';
  const VERTEX = `
    attribute vec2 uv;
    varying vec2 textureUV;
    uniform float clock;
    uniform float motion;
    void main() {
      vec2 p=uv;
      float side=uv.x<0.5?-1.0:1.0;
      // Continuous weights keep shoulders, waist and knees in the same surface.
      float arm=smoothstep(0.18,0.34,abs(uv.x-0.5))
        *smoothstep(0.075,0.19,uv.y)*(1.0-smoothstep(0.48,0.58,uv.y));
      float reach=clamp((uv.y-0.12)/0.40,0.0,1.0);
      float leg=smoothstep(0.49,0.66,uv.y);
      if(motion>0.5 && motion<1.5) {
        float stride=sin(clock*8.8);
        p.x+=0.027*stride*arm*reach;
        p.y-=0.026*stride*side*arm*reach;
        p.x+=0.012*stride*leg;
        p.y-=0.044*max(0.0,stride*side)*leg;
      } else if(motion>1.5) {
        float right=smoothstep(0.50,0.65,uv.x);
        p.x+=right*arm*reach*(0.018+0.012*sin(clock*5.0));
        p.y-=right*arm*reach*(0.022+0.008*sin(clock*5.0));
      } else {
        p.x+=0.0025*sin(clock*2.2)*arm*reach*side;
      }
      textureUV=uv;
      gl_Position=vec4((p.x*2.0-1.0)/1.20,(1.0-p.y*2.0)/1.12,0.0,1.0);
    }`;
  const FRAGMENT = `precision mediump float; varying vec2 textureUV;
    uniform sampler2D picture;
    void main(){gl_FragColor=texture2D(picture,textureUV);}`;
  class BodyMesh {
    constructor(){
      this.canvas=document.createElement('canvas');this.canvas.width=768;this.canvas.height=1152;
      this.gl=this.canvas.getContext('webgl',{alpha:true,antialias:true,premultipliedAlpha:true,preserveDrawingBuffer:true});
      this.source=null;
      if(!this.gl)return;
      const gl=this.gl;
      try{
        const compile=(type,source)=>{const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error('shader');return shader;};
        const program=gl.createProgram(),v=compile(gl.VERTEX_SHADER,VERTEX),f=compile(gl.FRAGMENT_SHADER,FRAGMENT);
        gl.attachShader(program,v);gl.attachShader(program,f);gl.linkProgram(program);
        if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error('link');
        gl.deleteShader(v);gl.deleteShader(f);gl.useProgram(program);this.program=program;
        const cols=32,rows=48,vertices=[],indices=[];
        for(let row=0;row<=rows;row++)for(let col=0;col<=cols;col++)vertices.push(col/cols,row/rows);
        for(let row=0;row<rows;row++)for(let col=0;col<cols;col++){const a=row*(cols+1)+col,b=a+1,c=a+cols+1,d=c+1;indices.push(a,c,b,b,c,d);}
        this.count=indices.length;
        const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.STATIC_DRAW);
        const location=gl.getAttribLocation(program,'uv');gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,2,gl.FLOAT,false,0,0);
        const elements=gl.createBuffer();gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,elements);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(indices),gl.STATIC_DRAW);
        this.clock=gl.getUniformLocation(program,'clock');this.motion=gl.getUniformLocation(program,'motion');
        this.texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,this.texture);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
        gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,true);gl.clearColor(0,0,0,0);
      }catch{this.gl=null;}
      this.canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();this.gl=null;});
    }
    setImage(source){
      if(this.source===source)return;this.source=source;
      if(this.gl){const gl=this.gl;gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source);}
    }
    draw(g,x,y,width,height,t,motion){
      if(!this.source)return;
      if(!this.gl){g.drawImage(this.source,x,y,width,height);return;}
      const gl=this.gl;gl.viewport(0,0,this.canvas.width,this.canvas.height);gl.clear(gl.COLOR_BUFFER_BIT);
      gl.useProgram(this.program);gl.uniform1f(this.clock,t);gl.uniform1f(this.motion,{idle:0,run:1,wave:2}[motion]||0);
      gl.drawElements(gl.TRIANGLES,this.count,gl.UNSIGNED_SHORT,0);
      g.drawImage(this.canvas,x-width*.10,y-height*.06,width*1.20,height*1.12);
    }
  }
  window.RunKKBodyMesh=BodyMesh;
})();
