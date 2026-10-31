export class RequestProblem extends Error { constructor(message:string,readonly status=400){super(message);} }
export async function readJsonBody<T>(request:Request,maxBytes=100000):Promise<T>{const text=await request.text();if(new TextEncoder().encode(text).length>maxBytes)throw new RequestProblem('Request is too large.',413);try{return JSON.parse(text) as T;}catch{throw new RequestProblem('Invalid JSON.');}}
const windows=new Map<string,{start:number;count:number}>();
export async function enforceRateLimit(_request:Request,scope:string,_fallback:string,limit:number,windowMs:number){const now=Date.now();let window=windows.get(scope);if(!window||now-window.start>windowMs){window={start:now,count:0};windows.set(scope,window);}if(++window.count>Math.max(limit,180))throw new RequestProblem('Please wait before trying again.',429);}
export function reportServerFailure(area:string,error:unknown){console.error(area,error instanceof Error?error.message:String(error));return 'LOCAL';}
