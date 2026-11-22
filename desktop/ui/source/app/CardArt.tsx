"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

export function cardImageUrl(source?: string) {
  if (!source) return "";
  if (source.startsWith("/")) return source;
  return `/api/card-image?url=${encodeURIComponent(source)}`;
}

export default function CardArt({ source, alt, className = "", publicObjectId, priority=false, fallback }: { source?: string; alt: string; className?: string; publicObjectId?:string; priority?:boolean;fallback?:ReactNode }) {
  const [failedSource, setFailedSource] = useState("");
  const [loadedSource,setLoadedSource]=useState("");
  const mountedAt=useRef(0);
  useEffect(()=>{mountedAt.current=performance.now();},[source]);
  const failed = !source || failedSource === source;
  const initials = alt.split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]).join("").toUpperCase();

  if (failed) {
    return <span className={`safe-card-art safe-card-fallback ${className}`} role="img" aria-label={`${alt} artwork unavailable`}>{fallback??<><b>{initials || "?"}</b><small>{alt}</small></>}</span>;
  }

  const loaded=loadedSource===source;
  return <>{publicObjectId&&!loaded&&<span className="card-art-loading" aria-hidden="true"><small>{alt}</small></span>}<img className={`safe-card-art ${className}`} src={cardImageUrl(source)} alt={alt} loading={priority||!!publicObjectId?"eager":"lazy"} decoding="async" onLoad={()=>{setLoadedSource(source);if(process.env.NODE_ENV==="development"&&publicObjectId)console.debug("[multiplayer] public card image visible",{objectId:publicObjectId,imageMs:Math.round((performance.now()-mountedAt.current)*10)/10});}} onError={() => setFailedSource(source)} /></>;
}
