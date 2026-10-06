import React,{useEffect,useState} from 'react';
import {ArrowRight} from 'lucide-react';

export type DesignProof={proofId:string;requestId:string;locationId:number;topic:string;revision:number;filename:string;contentType:string;status:string;createdAt:string};
export type DesignEvent={proofId:string;action:string;note:string;createdAt:string};

function ProofCard({proof,events,busy,onLoad,onDecision}:{proof:DesignProof;events:DesignEvent[];busy:boolean;onLoad:(proof:DesignProof)=>Promise<{url:string;filename:string}>;onDecision:(proof:DesignProof,decision:'APPROVE'|'REQUEST_CHANGES',note:string)=>Promise<void>}){
  const [file,setFile]=useState<{url:string;filename:string}|null>(null),[loading,setLoading]=useState(false),[changes,setChanges]=useState(''),[error,setError]=useState('');
  useEffect(()=>()=>{if(file)URL.revokeObjectURL(file.url);},[file]);
  const open=async()=>{setLoading(true);setError('');try{setFile(await onLoad(proof));}catch(e){setError(e instanceof Error?e.message:'Could not load the proof.');}finally{setLoading(false);}};
  return <article className="proof-card"><div className="proof-heading"><span className="status">{proof.status.replaceAll('_',' ')}</span><small>Revision {proof.revision} · {new Date(proof.createdAt).toLocaleDateString()}</small></div><h3>{proof.topic}</h3><p>Review the design from your store before approving production.</p>{!file?<button className="text-link" disabled={loading} onClick={open}>{loading?'Loading proof…':'View design proof'} <ArrowRight size={15}/></button>:<div className="proof-media">{proof.contentType.startsWith('image/')&&<img src={file.url} alt={`Design proof for ${proof.topic}, revision ${proof.revision}`}/>}<a href={file.url} download={file.filename}>Download {file.filename}</a></div>}{error&&<p role="alert">{error}</p>}
    {proof.status==='AWAITING_APPROVAL'&&<div className="proof-actions"><button className="primary" disabled={busy||loading||!file} onClick={()=>onDecision(proof,'APPROVE','')}>Approve design</button><label>Need a change?<textarea value={changes} maxLength={2000} onChange={e=>setChanges(e.target.value)} placeholder="Tell us exactly what to change"/></label><button className="secondary" disabled={busy||!file||!changes.trim()} onClick={()=>onDecision(proof,'REQUEST_CHANGES',changes.trim())}>Request changes</button></div>}
    {!!events.length&&<details className="proof-history"><summary>Approval history</summary><ol>{events.map((event,index)=><li key={`${event.createdAt}:${index}`}><strong>{event.action.replaceAll('_',' ')}</strong> · {new Date(event.createdAt).toLocaleString()}{event.note&&<p>{event.note}</p>}</li>)}</ol></details>}</article>;
}

export function DesignApprovals({proofs,history,busy,onLoad,onDecision}:{proofs:DesignProof[];history:DesignEvent[];busy:boolean;onLoad:(proof:DesignProof)=>Promise<{url:string;filename:string}>;onDecision:(proof:DesignProof,decision:'APPROVE'|'REQUEST_CHANGES',note:string)=>Promise<void>}){
  return <section className="design-approvals"><h2>Your design approvals</h2>{!proofs.length?<p>No designs are awaiting your review.</p>:<div className="proof-grid">{proofs.map(proof=><ProofCard key={proof.proofId} proof={proof} events={history.filter(event=>event.proofId===proof.proofId)} busy={busy} onLoad={onLoad} onDecision={onDecision}/>)}</div>}</section>;
}
