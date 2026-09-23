// Preview renderer: node preview.js <element>_wings.json <out.png>  (front, 3/4 and side views)
const fs=require("fs"),vm=require("vm"),zlib=require("zlib");
function mulberry32(a){return function(){a|=0;a=(a+0x6d2b79f5)|0;let t=Math.imul(a^(a>>>15),1|a);t=(t+Math.imul(t^(t>>>7),61|t))^t;return((t^(t>>>14))>>>0)/4294967296;};}
const COL={stone:[125,125,125],cobblestone:[110,110,110],oak_planks:[162,130,78],bricks:[150,97,83],stone_bricks:[122,121,122],grass_block:[95,160,60],dirt:[134,96,67],sand:[219,207,163],oak_log:[109,85,50],oak_leaves:[60,120,40],water:[50,90,210],white_wool:[234,236,237],black_wool:[21,21,26],red_wool:[161,39,35],blue_wool:[53,57,157],green_wool:[84,109,27],yellow_wool:[249,198,40],orange_wool:[240,118,19],purple_wool:[122,42,173],brown_wool:[114,72,41],gray_wool:[63,68,72],glass:[190,225,235],glowstone:[252,220,140],iron_block:[220,220,220],gold_block:[246,208,62]};
function load(file){const j=JSON.parse(fs.readFileSync(file));const inp=j.input||j;const g=new Map();
 const put=(x,y,z,m)=>{x=Math.round(x);y=Math.round(y);z=Math.round(z);if(x<0||x>255||y<0||y>255||z<0||z>255)return;g.set(x|(y<<8)|(z<<16),m)};
 const sb={rng:mulberry32(Number(inp.seed)||0),Math,block:put,
  box:(x1,y1,z1,x2,y2,z2,m)=>{for(let y=Math.min(y1,y2);y<=Math.max(y1,y2);y++)for(let z=Math.min(z1,z2);z<=Math.max(z1,z2);z++)for(let x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)put(x,y,z,m)},
  line:(x1,y1,z1,x2,y2,z2,m)=>{const s=Math.max(Math.abs(x2-x1),Math.abs(y2-y1),Math.abs(z2-z1),1);for(let i=0;i<=s;i++){const t=i/s;put(x1+(x2-x1)*t,y1+(y2-y1)*t,z1+(z2-z1)*t,m)}}};
 vm.createContext(sb);new vm.Script(inp.code).runInContext(sb);return g;}
function render(g,yawDeg,pitchDeg,W,H){
 const yaw=yawDeg*Math.PI/180,pit=pitchDeg*Math.PI/180,cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pit),sp=Math.sin(pit);
 let pts=[];let mnx=1e9,mxx=-1e9,mny=1e9,mxy=-1e9;
 const has=(x,y,z)=>x>=0&&y>=0&&z>=0&&x<256&&y<256&&z<256&&g.has(x|(y<<8)|(z<<16));
 for(const [k,m] of g){const x=k&255,y=(k>>8)&255,z=(k>>16)&255;
  const dx=x-128,dz=z-128;const rx=dx*cy-dz*sy,rz=dx*sy+dz*cy;const py=y*cp-rz*sp,depth=rz*cp+y*sp;
  pts.push([rx,py,depth,x,y,z,m]);mnx=Math.min(mnx,rx);mxx=Math.max(mxx,rx);mny=Math.min(mny,py);mxy=Math.max(mxy,py);}
 const sc=Math.min((W-40)/(mxx-mnx+1),(H-40)/(mxy-mny+1));const ox=W/2-(mnx+mxx)/2*sc,oy=H/2+(mny+mxy)/2*sc;
 const img=new Uint8Array(W*H*3),zb=new Float32Array(W*H).fill(1e9);
 for(let i=0;i<W*H;i++){const t=i/W/H;img[i*3]=28+t*10;img[i*3+1]=30+t*10;img[i*3+2]=40+t*20;}
 const r=Math.max(1,Math.ceil(sc*0.75));
 for(const [rx,py,d,x,y,z,m] of pts){
  // shading: light from up/front-left; count exposed faces
  let l=0.45; if(!has(x,y+1,z))l+=0.3; if(!has(x-1,y,z))l+=0.12; if(!has(x,y,z-1))l+=0.12; if(!has(x+1,y,z)&&!has(x,y,z+1))l+=0.03; if(!has(x,y-1,z))l-=0.08;
  let c=COL[m]||[255,0,255]; if(m==="glowstone"||m==="gold_block")l=Math.max(l,0.95);
  const px=Math.round(ox+rx*sc),pyy=Math.round(oy-py*sc);
  for(let a=-r;a<=r;a++)for(let b=-r;b<=r;b++){const X=px+a,Y=pyy+b;if(X<0||Y<0||X>=W||Y>=H)continue;const i=Y*W+X;const dd=d+(Math.abs(a)+Math.abs(b))*0.01/sc;if(dd<zb[i]){zb[i]=dd;
   let f=l; if(m==="glass"){f=l*0.9;} for(let q=0;q<3;q++)img[i*3+q]=Math.min(255,c[q]*f);}}}
 return img;}
function png(file,W,H,img){const raw=Buffer.alloc((W*3+1)*H);for(let y=0;y<H;y++){raw[y*(W*3+1)]=0;Buffer.from(img.buffer,y*W*3,W*3).copy(raw,y*(W*3+1)+1);}
 const crc=(b)=>{let c,t=[];for(let n=0;n<256;n++){c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;t[n]=c>>>0}c=0xffffffff;for(const x of b)c=t[(c^x)&255]^(c>>>8);return(c^0xffffffff)>>>0};
 const chunk=(ty,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(ty),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c])};
 const h=Buffer.alloc(13);h.writeUInt32BE(W,0);h.writeUInt32BE(H,4);h[8]=8;h[9]=2;
 fs.writeFileSync(file,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk("IHDR",h),chunk("IDAT",zlib.deflateSync(raw)),chunk("IEND",Buffer.alloc(0))]));}
const [,,inp,out]=process.argv;const g=load(inp);const W=700,H=620;
const views=[[0,0],[40,18],[90,0]];const all=new Uint8Array(W*views.length*H*3);
views.forEach(([yw,pt],vi)=>{const im=render(g,yw,pt,W,H);for(let y=0;y<H;y++)for(let x=0;x<W;x++)for(let q=0;q<3;q++)all[(y*W*views.length+vi*W+x)*3+q]=im[(y*W+x)*3+q];});
png(out,W*views.length,H,all);console.log("wrote",out);
