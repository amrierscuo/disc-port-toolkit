'use strict';
// Native mesh/UV/color buffers and original DDS pixels. Preview shader formulas
// are copied from the existing editor renderer; this is not the game's renderer.
(() => {
  const canvas = document.getElementById('scene');
  const caption = document.getElementById('caption');
  const error = document.getElementById('error');
  const INTERVAL = 2000, FADE = 220, FPS_LIMIT = 30;
  let gl, manifest, program, mixProgram, uniforms, mixUniforms, quad, white;
  let current = null, incoming = null, targets = [], index = 0;
  let running = true, released = false, rotating = true, initialized = false;
  let raf = 0, token = 0, controller = null, loading = false;
  let shownAt = 0, fadeAt = 0, lastFrame = -Infinity;
  const diagnostics = {renderer:'WebGL2 native mesh preview', renderedIds:[], errors:[], frames:0, activeModels:0, failed:false};
  const recordError = e => { diagnostics.failed=true;diagnostics.errors.push(String(e)); console.error(e); };
  const dot = (a,b) => a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
  const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
  const normalized = a => {const d=Math.hypot(...a);return a.map(v=>v/d);};
  async function fetchLocal(path, kind, signal) {
    if (!/^[A-Za-z0-9_./-]+$/.test(path) || path.includes('..') || path.startsWith('/')) throw Error('Unsafe asset path');
    const result = await fetch(path, {signal,cache:'no-store'});
    if (!result.ok) throw Error('Asset unavailable: '+path);
    return result[kind]();
  }
  function compile(vs, fs) {
    function shader(type, text) {
      const s=gl.createShader(type);gl.shaderSource(s,text);gl.compileShader(s);
      if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)) {const log=gl.getShaderInfoLog(s);gl.deleteShader(s);throw Error(log);}
      return s;
    }
    const v=shader(gl.VERTEX_SHADER,vs), f=shader(gl.FRAGMENT_SHADER,fs), p=gl.createProgram();
    gl.attachShader(p,v);gl.attachShader(p,f);gl.linkProgram(p);gl.deleteShader(v);gl.deleteShader(f);
    if(!gl.getProgramParameter(p,gl.LINK_STATUS)) {const log=gl.getProgramInfoLog(p);gl.deleteProgram(p);throw Error(log);}
    return p;
  }
  function makeGeometry(vertices, indices, dynamic=false) {
    const vao=gl.createVertexArray(), vb=gl.createBuffer(), ib=gl.createBuffer();
    gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vb);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);
    for(const [name,count,offset] of [['pos',3,0],['normal',3,12],['uv',2,24],['color',4,32]]) {
      const location=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(location);
      gl.vertexAttribPointer(location,count,gl.FLOAT,false,48,offset);
    }
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices,dynamic?gl.DYNAMIC_DRAW:gl.STATIC_DRAW);
    gl.bindVertexArray(null);return {vao,vb,ib,count:indices.length};
  }
  function deleteGeometry(geometry) {
    gl.deleteVertexArray(geometry.vao);gl.deleteBuffer(geometry.vb);gl.deleteBuffer(geometry.ib);
  }
  function dispose(model) {
    if(!model || model.disposed) return;
    model.disposed=true;
    for(const geometry of model.geometries) deleteGeometry(geometry);
    for(const texture of model.textures.values()) gl.deleteTexture(texture);
    diagnostics.activeModels--;
  }
  async function loadModel(at, request, signal) {
    const item=manifest.items[at], meta=await fetchLocal(manifest.meshes[item.mesh],'json',signal);
    const data=await fetchLocal(meta.binary,'arrayBuffer',signal);
    if(data.byteLength!==meta.bytes || meta.stride!==48 || item.materials.length!==meta.strips.length) throw Error('Invalid mesh layout');
    const model={item,meta,geometries:[],opaque:[],textures:new Map(),born:performance.now(),disposed:false};
    diagnostics.activeModels++;
    try {
      for(const material of item.materials) {
        if(!material.texture || model.textures.has(material.texture)) continue;
        const info=manifest.textures[material.texture];
        if(!info || info.width>512 || info.height>512) throw Error('Invalid native texture');
        const blob=await fetchLocal(info.asset,'blob',signal);
        const bitmap=await createImageBitmap(blob,{imageOrientation:'none',premultiplyAlpha:'none',colorSpaceConversion:'none'});
        try {
          if(request!==token || released) throw Error('Cancelled model');
          const texture=gl.createTexture();model.textures.set(material.texture,texture);gl.bindTexture(gl.TEXTURE_2D,texture);
          gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,false);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);
          gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL,gl.NONE);
          gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,bitmap);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT);
        } finally {bitmap.close();}
      }
      if(request!==token || released) throw Error('Cancelled model');
      const transparent=[];
      meta.strips.forEach((strip,i) => {
        const vertices=new Float32Array(data,strip.vertexOffset,strip.vertexCount*12);
        const indices=new Uint32Array(data,strip.indexOffset,strip.indexCount), material=item.materials[i];
        if(material.translucent) transparent.push({vertices,indices,material});
        else {const geometry=makeGeometry(vertices,indices);model.geometries.push(geometry);model.opaque.push({geometry,material});}
      });
      if(transparent.length) {
        const vertexCount=transparent.reduce((n,s)=>n+s.vertices.length,0), indexCount=transparent.reduce((n,s)=>n+s.indices.length,0);
        const vertices=new Float32Array(vertexCount), indices=new Uint32Array(indexCount), triangleMaterials=new Uint32Array(indexCount/3);
        let vo=0,io=0;
        transparent.forEach((strip,mi) => {
          vertices.set(strip.vertices,vo);
          for(let j=0;j<strip.indices.length;j++) indices[io+j]=strip.indices[j]+vo/12;
          triangleMaterials.fill(mi,io/3,(io+strip.indices.length)/3);
          vo+=strip.vertices.length;io+=strip.indices.length;
        });
        const count=indexCount/3, centers=new Float32Array(count*3);
        for(let triangle=0;triangle<count;triangle++) for(let axis=0;axis<3;axis++) {
          centers[triangle*3+axis]=(vertices[indices[triangle*3]*12+axis]+vertices[indices[triangle*3+1]*12+axis]+vertices[indices[triangle*3+2]*12+axis])/3;
        }
        const geometry=makeGeometry(vertices,indices,true);model.geometries.push(geometry);
        model.transparent={geometry,indices,centers,triangleMaterials,materials:transparent.map(s=>s.material),order:Array.from({length:count},(_,i)=>i),depths:new Float32Array(count),sorted:new Uint32Array(indexCount)};
      }
      return model;
    } catch(e) {dispose(model);throw e;}
  }
  function newTarget() {
    const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,canvas.width,canvas.height,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    const depth=gl.createRenderbuffer();gl.bindRenderbuffer(gl.RENDERBUFFER,depth);gl.renderbufferStorage(gl.RENDERBUFFER,gl.DEPTH_COMPONENT16,canvas.width,canvas.height);
    const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);
    gl.framebufferRenderbuffer(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.RENDERBUFFER,depth);
    if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE) throw Error('Framebuffer unavailable');
    return {texture,depth,fb};
  }
  function clearTargets() {
    for(const t of targets) {gl.deleteFramebuffer(t.fb);gl.deleteTexture(t.texture);gl.deleteRenderbuffer(t.depth);}
    targets=[];
  }
  function resize() {
    const ratio=Math.min(window.devicePixelRatio||1,2), box=canvas.getBoundingClientRect();
    const factor=Math.min(1,640/Math.max(box.width*ratio,box.height*ratio,1));
    const w=Math.max(1,Math.round(box.width*ratio*factor)), h=Math.max(1,Math.round(box.height*ratio*factor));
    if(canvas.width!==w || canvas.height!==h || !targets.length) {canvas.width=w;canvas.height=h;clearTargets();targets=[newTarget(),newTarget()];}
  }
  function material(model, value) {
    gl.uniform1i(uniforms.generated_material,value.generated?1:0);
    gl.uniform1i(uniforms.textured,value.texture?1:0);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,value.texture?model.textures.get(value.texture):white);
  }
  function renderModel(model,target,now) {
    gl.bindFramebuffer(gl.FRAMEBUFFER,target.fb);gl.viewport(0,0,canvas.width,canvas.height);
    gl.depthMask(true);gl.clearColor(0,0,0,0);gl.clearDepth(1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LESS);gl.disable(gl.CULL_FACE);gl.disable(gl.BLEND);gl.useProgram(program);
    const azimuth=.72+(rotating?(now-model.born)*.00015:0);
    const eye=normalized([Math.sin(azimuth)*.89,.46,Math.cos(azimuth)*.89]);
    const right=normalized(cross([0,1,0],eye)),up=cross(eye,right),meta=model.meta;
    const center=meta.lo.map((v,i)=>(v+meta.hi[i])/2), aspect=canvas.width/canvas.height;
    const hy=meta.radius*1.08*Math.max(1,1/aspect),hx=hy*aspect,depth=meta.radius*1.25+.1;
    const matrix=new Float32Array([right[0]/hx,up[0]/hy,-eye[0]/depth,0,right[1]/hx,up[1]/hy,-eye[1]/depth,0,right[2]/hx,up[2]/hy,-eye[2]/depth,0,-dot(right,center)/hx,-dot(up,center)/hy,dot(eye,center)/depth,1]);
    gl.uniformMatrix4fv(uniforms.mvp,false,matrix);gl.uniform1f(uniforms.lighting,1);
    gl.uniform3fv(uniforms.camera,center.map((v,i)=>v+eye[i]*Math.max(hx,hy)*4));
    gl.uniform3fv(uniforms.right,right);gl.uniform3fv(uniforms.up,up);gl.uniform3fv(uniforms.light_direction,[-.5,.8,.4]);
    gl.uniform1i(uniforms.tex,0);gl.uniform1i(uniforms.preserve_alpha,0);
    for(const batch of model.opaque) {material(model,batch.material);gl.bindVertexArray(batch.geometry.vao);gl.drawElements(gl.TRIANGLES,batch.geometry.count,gl.UNSIGNED_INT,0);}
    const t=model.transparent;
    if(t) {
      for(let n=0;n<t.order.length;n++) t.depths[n]=t.centers[n*3]*eye[0]+t.centers[n*3+1]*eye[1]+t.centers[n*3+2]*eye[2];
      t.order.sort((a,b)=>t.depths[a]-t.depths[b] || a-b);
      for(let n=0;n<t.order.length;n++) t.sorted.set(t.indices.subarray(t.order[n]*3,t.order[n]*3+3),n*3);
      gl.bindVertexArray(t.geometry.vao);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,t.geometry.ib);gl.bufferSubData(gl.ELEMENT_ARRAY_BUFFER,0,t.sorted);
      gl.enable(gl.BLEND);gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);
      gl.depthMask(false);gl.uniform1i(uniforms.preserve_alpha,1);
      for(let start=0;start<t.order.length;) {
        const mi=t.triangleMaterials[t.order[start]];let end=start+1;
        while(end<t.order.length && t.triangleMaterials[t.order[end]]===mi) end++;
        material(model,t.materials[mi]);gl.drawElements(gl.TRIANGLES,(end-start)*3,gl.UNSIGNED_INT,start*12);start=end;
      }
      gl.depthMask(true);gl.disable(gl.BLEND);
    }
  }
  function compose(amount) {
    gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,canvas.width,canvas.height);gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);
    gl.useProgram(mixProgram);gl.bindVertexArray(quad);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,targets[0].texture);gl.uniform1i(mixUniforms.a,0);
    gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,incoming?targets[1].texture:targets[0].texture);gl.uniform1i(mixUniforms.b,1);
    gl.uniform1f(mixUniforms.fade,amount);gl.drawArrays(gl.TRIANGLES,0,3);
  }
  function setCaption() {caption.textContent=current?current.item.name:'';}
  async function preload(at) {
    if(loading || incoming || released || !running) return;
    loading=true;controller=new AbortController();const request=token;
    try {
      const model=await loadModel(at,request,controller.signal);
      if(request!==token || !running || released) {dispose(model);return;}
      if(!current) {current=model;index=at;shownAt=performance.now();setCaption();}
      else incoming=model;
    } catch(e) {if(request===token && e.name!=='AbortError') recordError(e);}
    finally {if(request===token) {loading=false;controller=null;}}
  }
  function frame(now) {
    raf=0;if(!running || released || !initialized) return;
    raf=requestAnimationFrame(frame);
    if(now-lastFrame<1000/FPS_LIMIT) return;lastFrame=now;
    try {
      resize();
      if(!current) {preload(index);return;}
      if(!incoming && !loading) preload((index+1)%manifest.items.length);
      if(incoming && !fadeAt && now-shownAt>=INTERVAL) {fadeAt=now;incoming.born=now;}
      if(fadeAt && now-fadeAt>=FADE) {
        dispose(current);current=incoming;incoming=null;index=(index+1)%manifest.items.length;
        shownAt=fadeAt;fadeAt=0;setCaption();
      }
      renderModel(current,targets[0],now);
      if(!diagnostics.renderedIds.includes(current.item.id)) diagnostics.renderedIds.push(current.item.id);
      const amount=fadeAt?Math.min(1,(now-fadeAt)/FADE):0;
      if(incoming && fadeAt) renderModel(incoming,targets[1],now);
      compose(amount);diagnostics.frames++;
      const err=gl.getError();if(err!==gl.NO_ERROR) throw Error('WebGL error '+err);
    } catch(e) {recordError(e);error.hidden=false;stop();}
  }
  function start() {if(released)return;running=true;shownAt=performance.now();if(initialized && !raf) raf=requestAnimationFrame(frame);}
  function stop() {
    running=false;cancelAnimationFrame(raf);raf=0;token++;loading=false;
    if(controller) controller.abort();controller=null;
  }
  function release() {
    if(released)return;stop();released=true;
    if(gl) {
      dispose(current);dispose(incoming);current=incoming=null;clearTargets();
      if(program)gl.deleteProgram(program);if(mixProgram)gl.deleteProgram(mixProgram);
      if(white)gl.deleteTexture(white);if(quad)gl.deleteVertexArray(quad);
    }
    caption.textContent='';
  }
  async function showIndex(at) {
    if(!initialized || at<0 || at>=manifest.items.length || !Number.isInteger(at)) throw Error('Invalid index');
    stop();dispose(current);dispose(incoming);current=incoming=null;index=at;fadeAt=0;running=true;
    await preload(at);if(!raf)raf=requestAnimationFrame(frame);
  }
  async function initialize() {
    gl=canvas.getContext('webgl2',{alpha:true,premultipliedAlpha:true,antialias:true,depth:true,preserveDrawingBuffer:false,powerPreference:'low-power'});
    if(!gl)throw Error('WebGL2 unavailable');
    diagnostics.glRenderer=gl.getParameter(gl.RENDERER);diagnostics.glVersion=gl.getParameter(gl.VERSION);
    const loaded=await Promise.all([fetchLocal('index.json','json'),fetchLocal('shaders/preview.vert','text'),fetchLocal('shaders/preview.frag','text')]);
    if(released)return;manifest=loaded[0];program=compile(loaded[1],loaded[2]);uniforms={};
    for(const name of ['mvp','lighting','camera','right','up','light_direction','tex','preserve_alpha','generated_material','textured']) uniforms[name]=gl.getUniformLocation(program,name);
    mixProgram=compile('#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0,1);}',
      '#version 300 es\nprecision highp float;in vec2 uv;uniform sampler2D a,b;uniform float fade;out vec4 color;void main(){color=mix(texture(a,uv),texture(b,uv),fade);}');
    mixUniforms={};for(const name of ['a','b','fade'])mixUniforms[name]=gl.getUniformLocation(mixProgram,name);
    quad=gl.createVertexArray();white=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,white);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
    initialized=true;diagnostics.itemCount=manifest.items.length;if(running)start();
  }
  canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();recordError('WebGL context lost');error.hidden=false;stop();});
  window.BlinxCarousel={start,stop,release,setShowLabels:show=>{caption.hidden=!show;},setRotationEnabled:enabled=>{rotating=!!enabled;},showIndex,
    diagnostics:()=>({...diagnostics,index,loading,running,released,initialized,current:current?current.item.id:null}),
    ready:initialize().catch(e=>{recordError(e);error.hidden=false;stop();})};
})();
