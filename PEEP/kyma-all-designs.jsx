import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  Activity, Send, Download, Check, Mic, Volume2, Settings, Clock,
  ArrowLeft, ShieldCheck, Lock, FileAudio, Eye, EyeOff, User, Zap,
  ChevronRight, Upload, ArrowUpRight, ArrowDownLeft, ArrowDownToLine,
  Shield, Radio, Cpu, Bell, X
} from "lucide-react";

// ═══════════════════════════════════════════════════════════════════
// DESIGN 1 — Full Multi-View Dark App  (Kyma-newhelp.md)
// ═══════════════════════════════════════════════════════════════════
const D1_ASSETS = [
  { id: 'SOL', label: 'SOL Pay', symbol: '◎' },
  { id: 'SKR', label: 'SKR Tip', symbol: '⬡' },
  { id: 'SIGN', label: 'Cold Sign', symbol: '⬛' },
];

function D1_Wave({ isActive, color = "#14F195" }) {
  const [bars, setBars] = useState(Array(15).fill(4));
  useEffect(() => {
    let iv;
    if (isActive) iv = setInterval(() => setBars(prev => prev.map(() => Math.floor(Math.random() * 35) + 5)), 75);
    else setBars(Array(15).fill(3));
    return () => clearInterval(iv);
  }, [isActive]);
  return (
    <div className="flex items-center justify-center gap-[3px] h-12 w-full">
      {bars.map((h, i) => <div key={i} className="w-[3px] rounded-full transition-all duration-75" style={{ height: `${h}px`, backgroundColor: color, opacity: isActive ? 1 : 0.1 }} />)}
    </div>
  );
}

function Design1() {
  const [view, setView] = useState('home');
  const [status, setStatus] = useState('IDLE');
  const [asset, setAsset] = useState(D1_ASSETS[0]);
  const [amount, setAmount] = useState('');
  const [address, setAddress] = useState('');
  const [seedLen, setSeedLen] = useState(12);
  const [showPass, setShowPass] = useState(false);

  const nav = (v) => { setStatus('IDLE'); setAmount(''); setAddress(''); setView(v); };
  const trigger = () => {
    setStatus('ACTIVE');
    setTimeout(() => setStatus('SUCCESS'), 3500);
    setTimeout(() => { setStatus('IDLE'); if (view !== 'home') setView('home'); }, 5500);
  };

  const Toggle = ({ cur }) => (
    <div className="flex bg-zinc-900/40 p-1 border border-zinc-800/30 rounded-sm w-full max-w-[240px]">
      <button onClick={() => nav('transmit')} className={`flex-1 py-2 text-[9px] font-bold tracking-[0.2em] uppercase transition-all ${cur === 'transmit' ? 'bg-zinc-800 text-white' : 'text-zinc-600'}`}>Transmit</button>
      <button onClick={() => nav('receive')} className={`flex-1 py-2 text-[9px] font-bold tracking-[0.2em] uppercase transition-all ${cur === 'receive' ? 'bg-zinc-800 text-white' : 'text-zinc-600'}`}>Receive</button>
    </div>
  );

  if (view === 'transmit') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-8">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <Toggle cur="transmit" /><div className="w-5" />
      </header>
      <div className="w-full max-w-md flex flex-col gap-8">
        <div className="grid grid-cols-3 gap-3">
          {D1_ASSETS.map(a => (
            <button key={a.id} onClick={() => setAsset(a)} className={`py-5 border transition-all rounded-sm ${asset.id === a.id ? 'bg-white text-black border-white' : 'bg-transparent border-zinc-900 text-zinc-700'}`}>
              <div className="text-xl mb-1">{a.symbol}</div>
              <div className="text-[8px] font-bold tracking-widest uppercase">{a.label}</div>
            </button>
          ))}
        </div>
        <div className="h-20 bg-zinc-900/20 border-y border-zinc-900 flex items-center justify-center relative overflow-hidden">
          <D1_Wave isActive={status === 'ACTIVE'} color="#14F195" />
          {status === 'ACTIVE' && <div className="absolute inset-0 bg-[#14F195]/5 animate-pulse" />}
        </div>
        <div className="flex flex-col gap-8 py-4">
          {asset.id !== 'SIGN' && (
            <div className="flex flex-col items-center">
              <input type="number" placeholder="0.00" value={amount} onChange={e => setAmount(e.target.value)} className="bg-transparent text-6xl text-center w-full focus:outline-none font-light tracking-tighter placeholder:text-zinc-900" />
              <span className="text-[9px] tracking-[0.4em] text-zinc-700 uppercase font-bold mt-4">Amount ({asset.id})</span>
            </div>
          )}
          <div className="flex flex-col items-center px-4">
            <div className="relative w-full">
              <User size={12} className="absolute left-0 top-1/2 -translate-y-1/2 text-zinc-800" />
              <input type="text" placeholder="DESTINATION NODE" value={address} onChange={e => setAddress(e.target.value)} className="bg-transparent text-[11px] text-center w-full focus:outline-none font-mono tracking-widest py-3 border-b border-zinc-900 placeholder:text-zinc-900" />
            </div>
            <span className="text-[8px] tracking-[0.3em] text-zinc-800 uppercase font-bold mt-4">Recipient Identity</span>
          </div>
        </div>
        <button onClick={trigger} disabled={(asset.id !== 'SIGN' && !amount) || status !== 'IDLE'}
          className={`w-full py-6 font-bold tracking-[0.3em] uppercase transition-all flex items-center justify-center gap-3 rounded-sm ${status === 'SUCCESS' ? 'bg-[#14F195] text-black' : status === 'ACTIVE' ? 'bg-zinc-900 text-[#14F195]' : 'bg-white text-black disabled:opacity-5'}`}>
          {status === 'SUCCESS' ? <Check size={18}/> : status === 'ACTIVE' ? <Activity size={18} className="animate-pulse"/> : <Zap size={18}/>}
          {status === 'SUCCESS' ? 'Transmitted' : status === 'ACTIVE' ? 'Broadcasting mFSK' : 'Initiate Burst'}
        </button>
      </div>
    </div>
  );

  if (view === 'receive') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-8">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <Toggle cur="receive" /><div className="w-5" />
      </header>
      <div className="w-full max-w-md flex flex-col items-center justify-center flex-grow gap-12 pb-24">
        <div className={`w-64 h-64 border flex items-center justify-center transition-all duration-700 relative rounded-sm ${status === 'ACTIVE' ? 'border-[#9945FF] scale-105 shadow-[0_0_50px_rgba(153,69,255,0.1)]' : 'border-zinc-900'}`}>
          <div className="absolute inset-0 flex items-center justify-center px-12 opacity-40">
            <D1_Wave isActive={status === 'ACTIVE'} color="#9945FF" />
          </div>
          <div className="bg-black p-4 relative z-10">
            {status === 'SUCCESS' ? <Check size={48} className="text-[#14F195]" /> : <Mic size={40} className={status === 'ACTIVE' ? 'text-[#9945FF]' : 'text-zinc-900'} />}
          </div>
        </div>
        <div className="text-center">
          <h2 className={`text-[11px] font-bold tracking-[0.5em] uppercase mb-3 ${status === 'ACTIVE' ? 'text-[#9945FF]' : 'text-zinc-600'}`}>
            {status === 'ACTIVE' ? 'Decoding Burst...' : status === 'SUCCESS' ? 'Decode Complete' : 'System Listening'}
          </h2>
          <p className="text-[9px] text-zinc-800 tracking-[0.2em] uppercase font-mono">{status === 'ACTIVE' ? 'mFSK Spectrum Active' : 'Waiting for Acoustic Sync'}</p>
        </div>
        {status === 'IDLE' && <button onClick={trigger} className="w-full py-6 bg-white text-black font-bold tracking-[0.3em] uppercase active:scale-95 transition-all rounded-sm">Open Gateway</button>}
      </div>
    </div>
  );

  if (view === 'backup') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-12">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <span className="text-[10px] font-bold tracking-[0.4em] uppercase text-zinc-600">Secure Export</span>
        <div className="w-5" />
      </header>
      <main className="w-full max-w-md flex flex-col gap-10 overflow-y-auto pb-12">
        <section>
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-700">Mnemonic Seed</h3>
            <div className="flex bg-black border border-zinc-900 p-1">
              {[12, 24].map(l => <button key={l} onClick={() => setSeedLen(l)} className={`px-4 py-1 text-[9px] font-bold tracking-tighter uppercase transition-all ${seedLen === l ? 'bg-zinc-900 text-white' : 'text-zinc-800'}`}>{l}</button>)}
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {Array.from({ length: seedLen }).map((_, i) => (
              <div key={i} className="relative">
                <span className="absolute left-2 top-2 text-[7px] text-zinc-800 font-mono">{i+1}</span>
                <input type="text" className="w-full bg-zinc-900/20 border border-zinc-900 py-4 px-2 pl-6 text-[10px] font-mono focus:outline-none focus:border-zinc-700 rounded-sm" placeholder="..." />
              </div>
            ))}
          </div>
        </section>
        <section>
          <h3 className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-700 mb-6 text-center">Encryption Password</h3>
          <div className="relative border-b border-zinc-900">
            <input type={showPass ? "text" : "password"} className="w-full bg-transparent py-4 text-center text-sm tracking-[0.3em] focus:outline-none placeholder:text-zinc-900" placeholder="••••••••••••" />
            <button onClick={() => setShowPass(!showPass)} className="absolute right-0 top-1/2 -translate-y-1/2 text-zinc-800 hover:text-white">
              {showPass ? <EyeOff size={16}/> : <Eye size={16}/>}
            </button>
          </div>
        </section>
        <section className="bg-zinc-950 border border-zinc-900 p-8 flex flex-col items-center gap-6 rounded-sm">
          <div className="w-16 h-16 rounded-full border border-zinc-900 flex items-center justify-center text-zinc-800"><FileAudio size={28} strokeWidth={1}/></div>
          <div className="text-center">
            <p className="text-[10px] font-bold tracking-widest uppercase mb-1">Acoustic Camouflage</p>
            <p className="text-[9px] text-zinc-800 font-bold uppercase">Steganographic Carrier</p>
          </div>
          <button className="text-[9px] font-bold tracking-widest uppercase border border-zinc-900 px-8 py-3 hover:bg-zinc-900 transition-all text-zinc-400">Upload WAV Cover</button>
        </section>
        <button onClick={trigger} className="w-full py-6 bg-white text-black font-bold tracking-[0.3em] uppercase rounded-sm">Initiate Backup</button>
      </main>
    </div>
  );

  if (view === 'settings') return (
    <div className="min-h-screen bg-black flex flex-col items-center justify-center p-12 text-center">
      <h2 className="text-[10px] font-bold tracking-[0.5em] uppercase text-zinc-800 mb-8">Acoustic Logic Configuration</h2>
      <div className="w-full max-w-xs space-y-4 mb-12">
        <div className="flex justify-between items-center py-4 border-b border-zinc-900"><span className="text-[9px] font-bold uppercase tracking-widest text-zinc-600">Sample Rate</span><span className="text-[9px] font-mono text-zinc-400">48,000 Hz</span></div>
        <div className="flex justify-between items-center py-4 border-b border-zinc-900"><span className="text-[9px] font-bold uppercase tracking-widest text-zinc-600">Protocol</span><span className="text-[9px] font-mono text-zinc-400">mFSK V2</span></div>
      </div>
      <button onClick={() => setView('home')} className="px-10 py-3 border border-zinc-900 text-[10px] font-bold tracking-widest uppercase text-zinc-600 hover:text-white transition-colors">Return</button>
    </div>
  );

  return (
    <div className="min-h-screen bg-black text-white font-sans flex flex-col items-center select-none pb-24">
      <header className="mt-20 mb-16 flex flex-col items-center">
        <div className="flex items-end gap-[3px] mb-8 h-8">{[12,28,16,22,12].map((h,i) => <div key={i} className="w-[3px] bg-white opacity-90" style={{height:`${h}px`}}/>)}</div>
        <h1 className="text-3xl font-light tracking-[0.5em] uppercase text-white">Kyma</h1>
        <div className="flex items-center gap-2 mt-4 text-zinc-600"><ShieldCheck size={12}/><span className="text-[9px] font-bold tracking-[0.3em] uppercase">Acoustic Cryptography</span></div>
      </header>
      <main className="w-full max-w-md px-10 flex flex-col gap-10">
        <div className="grid grid-cols-2 gap-6">
          <button onClick={() => nav('transmit')} className="group flex flex-col items-center justify-center gap-6 p-10 bg-zinc-900/30 border border-zinc-800/40 hover:border-zinc-700 transition-all rounded-sm">
            <Volume2 size={32} strokeWidth={1} className="text-zinc-500 group-hover:text-white"/><span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500">Transmit</span>
          </button>
          <button onClick={() => nav('receive')} className="group flex flex-col items-center justify-center gap-6 p-10 bg-zinc-900/30 border border-zinc-800/40 hover:border-zinc-700 transition-all rounded-sm">
            <Mic size={32} strokeWidth={1} className="text-zinc-500 group-hover:text-white"/><span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500">Receive</span>
          </button>
        </div>
        <section className="flex flex-col gap-4">
          <div className="flex items-center gap-4"><div className="h-[1px] bg-zinc-900 flex-grow"/><span className="text-[8px] font-black tracking-[0.3em] uppercase text-zinc-700">Vault Infrastructure</span><div className="h-[1px] bg-zinc-900 flex-grow"/></div>
          <div className="bg-zinc-900/10 border border-zinc-900 rounded-sm">
            <div className="p-5 flex items-center justify-between border-b border-zinc-900">
              <div className="flex items-center gap-4"><div className="p-2.5 bg-zinc-900"><Lock size={14} className="text-zinc-500"/></div>
                <div><p className="text-[10px] font-bold tracking-widest uppercase">Primary Keychain</p><p className="text-[9px] font-mono text-zinc-600 mt-1">ID: 0x8F...A1E • 128-bit Salt</p></div>
              </div>
              <ChevronRight size={14} className="text-zinc-800"/>
            </div>
            <div className="grid grid-cols-2">
              <button onClick={() => nav('backup')} className="py-4 text-[9px] font-bold tracking-widest uppercase text-zinc-500 hover:text-white border-r border-zinc-900 flex items-center justify-center gap-2 transition-colors"><Upload size={12}/> Backup</button>
              <button className="py-4 text-[9px] font-bold tracking-widest uppercase text-zinc-500 hover:text-white flex items-center justify-center gap-2 transition-colors"><Download size={12}/> Restore</button>
            </div>
          </div>
        </section>
      </main>
      <footer className="fixed bottom-12 w-full max-w-md px-10 flex justify-between items-center" style={{zIndex:10}}>
        <div className="flex items-center gap-2 text-zinc-700"><Clock size={10}/><span className="text-[9px] font-bold uppercase tracking-widest">Last Backup: 2h ago</span></div>
        <button onClick={() => setView('settings')} className="text-zinc-700 hover:text-white transition-colors"><Settings size={18} strokeWidth={1.5}/></button>
      </footer>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 2 — SonicVault Circle  (Kymaaah.md)
// ═══════════════════════════════════════════════════════════════════
function Design2() {
  const [activeState, setActiveState] = useState('idle');
  const [waveHeights, setWaveHeights] = useState(Array(15).fill(20));

  useEffect(() => {
    let iv;
    if (activeState === 'transmitting' || activeState === 'receiving') {
      iv = setInterval(() => setWaveHeights(prev => prev.map(() => 10 + Math.random() * 40)), 150);
    } else {
      setWaveHeights(Array(15).fill(12));
    }
    return () => clearInterval(iv);
  }, [activeState]);

  const handleAction = (action) => { setActiveState(action); setTimeout(() => setActiveState('idle'), 4000); };

  return (
    <div className="min-h-screen bg-[#0A0A0A] text-zinc-100 font-sans flex flex-col items-center justify-between p-6 sm:p-8 select-none">
      <header className="w-full max-w-md flex flex-col items-center mt-8">
        <div className="flex items-end justify-center space-x-1 mb-6 h-8">
          {[1,2,3,4,5].map((_,i) => <div key={i} className="w-1.5 bg-zinc-100 rounded-full" style={{height:`${[12,24,32,20,16][i]}px`,opacity:0.9}}/>)}
        </div>
        <h1 className="text-3xl font-light tracking-[0.35em] ml-2 text-white uppercase">Kyma</h1>
        <div className="flex items-center mt-3 space-x-2 text-zinc-500"><ShieldCheck size={14}/><span className="text-[10px] tracking-widest uppercase font-semibold">SonicVault Secure</span></div>
      </header>

      <div className="flex-grow flex flex-col items-center justify-center w-full max-w-md">
        <div className="relative w-full aspect-square max-w-[280px] rounded-full border border-zinc-800/50 bg-[#111] flex items-center justify-center shadow-inner overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-zinc-800/20 via-transparent to-transparent opacity-50"/>
          <div className="flex items-center space-x-1.5 z-10">
            {waveHeights.map((h,i) => (
              <div key={i} className={`w-1.5 rounded-full transition-all duration-150 ease-out ${activeState==='transmitting'?'bg-[#00FFA3] shadow-[0_0_12px_#00FFA3]':activeState==='receiving'?'bg-[#9945FF] shadow-[0_0_12px_#9945FF]':'bg-zinc-700'}`} style={{height:`${h}px`,minHeight:'4px'}}/>
            ))}
          </div>
          <div className="absolute bottom-10 text-[11px] tracking-widest uppercase text-zinc-500 font-medium">
            {activeState==='idle'?'15.0 - 19.4 kHz Ready':activeState==='transmitting'?'Broadcasting...':'Listening...'}
          </div>
        </div>
      </div>

      <main className="w-full max-w-md flex flex-col gap-8 mb-4">
        <div className="grid grid-cols-2 gap-4">
          <button onClick={() => handleAction('transmitting')} className={`flex flex-col items-center justify-center gap-3 p-5 rounded-2xl border transition-all active:scale-95 ${activeState==='transmitting'?'bg-[#00FFA3]/10 border-[#00FFA3]/50 text-[#00FFA3]':'bg-zinc-900 border-zinc-800 text-zinc-100 hover:bg-zinc-800/80'}`}>
            <ArrowUpRight strokeWidth={1.5} size={28}/><span className="text-sm font-medium tracking-wide">Transmit</span>
          </button>
          <button onClick={() => handleAction('receiving')} className={`flex flex-col items-center justify-center gap-3 p-5 rounded-2xl border transition-all active:scale-95 ${activeState==='receiving'?'bg-[#9945FF]/10 border-[#9945FF]/50 text-[#9945FF]':'bg-zinc-900 border-zinc-800 text-zinc-100 hover:bg-zinc-800/80'}`}>
            <ArrowDownLeft strokeWidth={1.5} size={28}/><span className="text-sm font-medium tracking-wide">Receive</span>
          </button>
        </div>
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-4 px-2"><div className="h-px bg-zinc-800 flex-grow"/><span className="text-[10px] uppercase tracking-widest text-zinc-600 font-semibold">Vault</span><div className="h-px bg-zinc-800 flex-grow"/></div>
          <div className="grid grid-cols-2 gap-3">
            <button className="flex items-center justify-center gap-2 py-3.5 rounded-xl bg-zinc-900/50 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors active:scale-95"><Upload size={16} strokeWidth={2}/><span className="text-xs font-medium tracking-wide uppercase">Backup</span></button>
            <button className="flex items-center justify-center gap-2 py-3.5 rounded-xl bg-zinc-900/50 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors active:scale-95"><Download size={16} strokeWidth={2}/><span className="text-xs font-medium tracking-wide uppercase">Restore</span></button>
          </div>
        </div>
      </main>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 3 — Keyring Multi-View v2  (Kyma_again.md)
// ═══════════════════════════════════════════════════════════════════
const D3_ASSETS = [
  { id: 'SOL', label: 'SOL Pay', symbol: '◎' },
  { id: 'SKR', label: 'SKR Tip', symbol: '⬡' },
  { id: 'SIGN', label: 'Cold Sign', symbol: '⬛' },
];

function D3_Wave({ isActive, color = "#14F195" }) {
  const [bars, setBars] = useState(Array(15).fill(4));
  useEffect(() => {
    let iv;
    if (isActive) iv = setInterval(() => setBars(prev => prev.map(() => Math.floor(Math.random() * 35) + 5)), 70);
    else setBars(Array(15).fill(3));
    return () => clearInterval(iv);
  }, [isActive]);
  return (
    <div className="flex items-center justify-center gap-1 h-12 w-full">
      {bars.map((h,i) => <div key={i} className="w-1 rounded-full transition-all duration-75" style={{height:`${h}px`,backgroundColor:color,opacity:isActive?1:0.15}}/>)}
    </div>
  );
}

function Design3() {
  const [view, setView] = useState('home');
  const [status, setStatus] = useState('IDLE');
  const [asset, setAsset] = useState(D3_ASSETS[0]);
  const [amount, setAmount] = useState('');
  const [address, setAddress] = useState('');
  const [seedLen, setSeedLen] = useState(12);
  const [showPass, setShowPass] = useState(false);

  const isIdle = status === 'IDLE', isActive = status === 'ACTIVE', isSuccess = status === 'SUCCESS';
  const nav = (v) => { setStatus('IDLE'); setView(v); };
  const trigger = () => {
    setStatus('ACTIVE');
    setTimeout(() => setStatus('SUCCESS'), 3500);
    setTimeout(() => { setStatus('IDLE'); if (view !== 'home') setView('home'); }, 5500);
  };

  if (view === 'transmit') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-8">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <div className="flex bg-zinc-900/50 p-1 rounded-sm border border-zinc-800/40">
          <button className="px-6 py-1.5 text-[9px] font-bold tracking-widest uppercase bg-zinc-800">TX</button>
          <button onClick={() => nav('receive')} className="px-6 py-1.5 text-[9px] font-bold tracking-widest uppercase text-zinc-600">RX</button>
        </div>
        <div className="w-5"/>
      </header>
      <div className="w-full max-w-md flex flex-col gap-10">
        <div className="grid grid-cols-3 gap-3">
          {D3_ASSETS.map(a => (
            <button key={a.id} onClick={() => setAsset(a)} className={`py-5 border transition-all ${asset.id===a.id?'bg-white text-black border-white':'bg-transparent border-zinc-900 text-zinc-700'}`}>
              <div className="text-xl mb-1">{a.symbol}</div><div className="text-[8px] font-bold tracking-tighter uppercase">{a.label}</div>
            </button>
          ))}
        </div>
        <div className="h-20 bg-zinc-900/20 border-y border-zinc-900 flex items-center justify-center relative overflow-hidden">
          <D3_Wave isActive={isActive} color="#14F195"/>
          {isActive && <div className="absolute inset-0 bg-[#14F195]/5 animate-pulse"/>}
        </div>
        <div className="flex flex-col gap-10">
          {asset.id !== 'SIGN' && (
            <div className="flex flex-col items-center">
              <input type="number" placeholder="0.00" value={amount} onChange={e=>setAmount(e.target.value)} className="bg-transparent text-6xl text-center w-full focus:outline-none font-light tracking-tighter placeholder:text-zinc-900"/>
              <span className="text-[9px] tracking-[0.4em] text-zinc-700 uppercase font-bold mt-4">Amount ({asset.id})</span>
            </div>
          )}
          <div className="flex flex-col items-center px-4">
            <div className="relative w-full">
              <User size={12} className="absolute left-0 top-1/2 -translate-y-1/2 text-zinc-800"/>
              <input type="text" placeholder="DESTINATION NODE" value={address} onChange={e=>setAddress(e.target.value)} className="bg-transparent text-[11px] text-center w-full focus:outline-none font-mono tracking-widest py-3 border-b border-zinc-900 placeholder:text-zinc-900"/>
            </div>
            <span className="text-[8px] tracking-[0.3em] text-zinc-800 uppercase font-bold mt-4">Recipient Address</span>
          </div>
        </div>
        <button onClick={trigger} disabled={(asset.id!=='SIGN'&&!amount)||!isIdle}
          className={`w-full py-6 font-bold tracking-[0.3em] uppercase transition-all flex items-center justify-center gap-3 ${isSuccess?'bg-[#14F195] text-black':isActive?'bg-zinc-900 text-[#14F195]':'bg-white text-black disabled:opacity-5'}`}>
          {isSuccess?<Check size={18}/>:isActive?<Volume2 size={18} className="animate-pulse"/>:<Zap size={18}/>}
          {isSuccess?'Sent':isActive?'Broadcasting mFSK':'Initiate Burst'}
        </button>
      </div>
    </div>
  );

  if (view === 'receive') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-8">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <div className="flex bg-zinc-900/50 p-1 rounded-sm border border-zinc-800/40">
          <button onClick={() => nav('transmit')} className="px-6 py-1.5 text-[9px] font-bold tracking-widest uppercase text-zinc-600">TX</button>
          <button className="px-6 py-1.5 text-[9px] font-bold tracking-widest uppercase bg-zinc-800">RX</button>
        </div>
        <div className="w-5"/>
      </header>
      <div className="w-full max-w-md flex flex-col items-center justify-center flex-grow gap-12 pb-24">
        <div className={`w-64 h-64 border flex items-center justify-center transition-all duration-700 relative ${isActive?'border-[#9945FF] scale-105':'border-zinc-900'}`}>
          <div className="absolute inset-0 flex items-center justify-center px-12 opacity-50"><D3_Wave isActive={isActive} color="#9945FF"/></div>
          <div className="bg-black p-4 relative z-10">{isSuccess?<Check size={48} className="text-[#14F195]"/>:<Mic size={40} className={isActive?'text-[#9945FF]':'text-zinc-800'}/>}</div>
        </div>
        <div className="text-center">
          <h2 className={`text-[11px] font-bold tracking-[0.5em] uppercase mb-3 ${isActive?'text-[#9945FF]':'text-zinc-500'}`}>{isActive?'Decoding Signal...':isSuccess?'Acoustic Decode Success':'Ready to Receive'}</h2>
          <p className="text-[9px] text-zinc-700 tracking-[0.2em] uppercase font-mono">{isActive?'Listening on 18.4kHz':'Proximity Protocol Active'}</p>
        </div>
        {isIdle && <button onClick={trigger} className="w-full py-6 bg-white text-black font-bold tracking-[0.3em] uppercase transition-all active:scale-95">Open Gateway</button>}
      </div>
    </div>
  );

  if (view === 'backup') return (
    <div className="min-h-screen bg-black text-white p-8 flex flex-col items-center overflow-y-auto">
      <header className="w-full max-w-md flex items-center justify-between mt-8 mb-12">
        <button onClick={() => nav('home')} className="text-zinc-600 hover:text-white"><ArrowLeft size={20}/></button>
        <span className="text-[10px] font-bold tracking-[0.4em] uppercase text-zinc-500">Vault Backup</span><div className="w-5"/>
      </header>
      <main className="w-full max-w-md flex flex-col gap-12 pb-12">
        <section>
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-700">Mnemonic Seed</h3>
            <div className="flex bg-zinc-950 p-1 border border-zinc-900">{[12,24].map(l=><button key={l} onClick={()=>setSeedLen(l)} className={`px-4 py-1 text-[9px] font-bold tracking-tighter uppercase transition-all ${seedLen===l?'bg-zinc-900 text-white':'text-zinc-800'}`}>{l} words</button>)}</div>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {Array.from({length:seedLen}).map((_,i)=>(
              <div key={i} className="relative"><span className="absolute left-2 top-2 text-[7px] text-zinc-800 font-mono">{i+1}</span>
                <input type="text" className="w-full bg-zinc-900/40 border border-zinc-900 py-4 px-2 pl-6 text-[10px] font-mono focus:outline-none focus:border-zinc-700" placeholder="..."/>
              </div>
            ))}
          </div>
        </section>
        <section>
          <h3 className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-700 mb-6 text-center">Encryption Password</h3>
          <div className="relative">
            <input type={showPass?"text":"password"} className="w-full bg-transparent border-b border-zinc-900 py-4 text-center text-sm tracking-[0.3em] focus:outline-none focus:border-white transition-all placeholder:text-zinc-900" placeholder="••••••••••••"/>
            <button onClick={()=>setShowPass(!showPass)} className="absolute right-0 top-1/2 -translate-y-1/2 text-zinc-800 hover:text-white">{showPass?<EyeOff size={16}/>:<Eye size={16}/>}</button>
          </div>
        </section>
        <section className="bg-zinc-950 border border-zinc-900 p-8 flex flex-col items-center gap-6">
          <div className="w-16 h-16 rounded-full border border-zinc-900 flex items-center justify-center text-zinc-800"><FileAudio size={28} strokeWidth={1}/></div>
          <div className="text-center"><p className="text-[10px] font-bold tracking-widest uppercase mb-1">Acoustic Cover</p><p className="text-[9px] text-zinc-800 font-bold uppercase">Optional Steganography</p></div>
          <button className="text-[10px] font-bold tracking-widest uppercase border border-zinc-800 px-6 py-3 hover:bg-zinc-900 transition-all">Upload WAV / MP3</button>
        </section>
        <button onClick={trigger} className="w-full py-6 bg-white text-black font-bold tracking-[0.3em] uppercase">Generate Backup</button>
      </main>
    </div>
  );

  if (view === 'settings') return (
    <div className="min-h-screen bg-black flex flex-col items-center justify-center p-12">
      <h2 className="text-[10px] font-bold tracking-[0.5em] uppercase text-zinc-800 mb-8">System Configuration</h2>
      <button onClick={()=>setView('home')} className="px-10 py-3 border border-zinc-900 text-[10px] font-bold tracking-widest uppercase text-zinc-600">Close</button>
    </div>
  );

  return (
    <div className="min-h-screen bg-black text-white font-sans flex flex-col items-center select-none pb-24">
      <header className="mt-20 mb-16 flex flex-col items-center">
        <div className="flex items-end gap-1 mb-8 h-8">{[12,28,16,22,12].map((h,i)=><div key={i} className="w-1 bg-white" style={{height:`${h}px`}}/>)}</div>
        <h1 className="text-3xl font-light tracking-[0.5em] uppercase">Kyma</h1>
        <div className="flex items-center gap-2 mt-4 text-zinc-600"><ShieldCheck size={12}/><span className="text-[9px] font-bold tracking-[0.3em] uppercase">Acoustic Cryptography</span></div>
      </header>
      <main className="w-full max-w-md px-10 flex flex-col gap-10">
        <div className="grid grid-cols-2 gap-6">
          <button onClick={()=>nav('transmit')} className="group flex flex-col items-center justify-center gap-6 p-8 bg-zinc-900/40 border border-zinc-800/50 hover:border-zinc-700 transition-all rounded-sm active:bg-zinc-900">
            <Volume2 size={32} strokeWidth={1.2} className="text-zinc-500 group-hover:text-white"/><span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500">Transmit</span>
          </button>
          <button onClick={()=>nav('receive')} className="group flex flex-col items-center justify-center gap-6 p-8 bg-zinc-900/40 border border-zinc-800/50 hover:border-zinc-700 transition-all rounded-sm active:bg-zinc-900">
            <Mic size={32} strokeWidth={1.2} className="text-zinc-500 group-hover:text-white"/><span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500">Receive</span>
          </button>
        </div>
        <section className="flex flex-col gap-4">
          <div className="flex items-center gap-4"><div className="h-[1px] bg-zinc-900 flex-grow"/><span className="text-[8px] font-black tracking-[0.3em] uppercase text-zinc-700">Vault Mgmt</span><div className="h-[1px] bg-zinc-900 flex-grow"/></div>
          <div className="bg-zinc-900/20 border border-zinc-900 rounded-sm">
            <div className="p-4 flex items-center justify-between border-b border-zinc-900">
              <div className="flex items-center gap-4"><div className="p-2 bg-zinc-900"><Lock size={14} className="text-zinc-500"/></div>
                <div><p className="text-[10px] font-bold tracking-widest uppercase">Primary Keyring</p><p className="text-[9px] font-mono text-zinc-600 mt-0.5">SHA256: 8F2...A1E</p></div>
              </div>
              <ChevronRight size={14} className="text-zinc-800"/>
            </div>
            <div className="grid grid-cols-2">
              <button onClick={()=>nav('backup')} className="py-4 text-[9px] font-bold tracking-widest uppercase text-zinc-500 hover:text-white border-r border-zinc-900 flex items-center justify-center gap-2"><Upload size={12}/> Backup</button>
              <button className="py-4 text-[9px] font-bold tracking-widest uppercase text-zinc-500 hover:text-white flex items-center justify-center gap-2"><Download size={12}/> Restore</button>
            </div>
          </div>
        </section>
      </main>
      <footer className="fixed bottom-12 w-full max-w-md px-10 flex justify-between items-center" style={{zIndex:10}}>
        <div className="flex items-center gap-2"><Clock size={12} className="text-zinc-800"/><span className="text-[9px] text-zinc-700 font-bold uppercase tracking-widest">Last Sync: 14m ago</span></div>
        <button onClick={()=>setView('settings')} className="text-zinc-600 hover:text-white"><Settings size={18} strokeWidth={1.5}/></button>
      </footer>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 4 — Home + Full Settings  (Kymaaaaaa.md)
// ═══════════════════════════════════════════════════════════════════
function D4_SettingRow({ icon, label, value }) {
  return (
    <div className="py-5 px-2 flex justify-between items-center border-b border-zinc-800/60 group hover:bg-zinc-900/20 transition-colors">
      <div className="flex items-center gap-4"><span className="text-zinc-600">{icon}</span><span className="text-[10px] uppercase tracking-widest font-bold text-zinc-400">{label}</span></div>
      <span className="font-mono text-[10px] text-zinc-300">{value}</span>
    </div>
  );
}
function D4_SettingToggle({ label, description, active: initActive }) {
  const [active, setActive] = useState(initActive);
  return (
    <div className="py-5 px-2 flex justify-between items-center border-b border-zinc-800/60 cursor-pointer" onClick={()=>setActive(!active)}>
      <div className="flex flex-col gap-1"><span className="text-[10px] uppercase tracking-widest font-bold text-zinc-400">{label}</span><span className="text-[9px] text-zinc-600 uppercase tracking-tighter">{description}</span></div>
      <div className={`w-10 h-5 border flex items-center p-1 transition-colors ${active?'bg-white border-white':'bg-transparent border-zinc-700'}`}>
        <div className={`w-3 h-3 transition-transform ${active?'translate-x-5 bg-black':'translate-x-0 bg-zinc-700'}`}/>
      </div>
    </div>
  );
}

function Design4() {
  const [view, setView] = useState('home');

  if (view === 'settings') return (
    <div className="min-h-screen bg-[#0D0D0D] text-[#E0E0E0] font-sans flex flex-col items-center select-none relative pb-12">
      <header className="w-full max-w-md flex items-center justify-between px-8 pt-16 mb-12">
        <button onClick={()=>setView('home')} className="p-2 -ml-2 text-zinc-500 hover:text-white transition-colors"><ArrowLeft size={20} strokeWidth={1.5}/></button>
        <h2 className="text-[11px] font-bold tracking-[0.4em] uppercase text-white">System Settings</h2>
        <div className="w-8"/>
      </header>
      <main className="w-full max-w-md flex-grow flex flex-col gap-1 px-8">
        <section className="mb-10">
          <h3 className="text-[9px] tracking-[0.2em] uppercase text-zinc-600 font-bold mb-6 px-2">Acoustic Parameters</h3>
          <div className="flex flex-col border-t border-zinc-800/60">
            <D4_SettingRow icon={<Radio size={14}/>} label="Frequency Range" value="15.0 - 19.4 kHz"/>
            <D4_SettingRow icon={<Cpu size={14}/>} label="Protocol" value="mFSK / 48kHz"/>
            <D4_SettingToggle label="Ultra-Sonic Only" description="Disables audible fallbacks" active={true}/>
          </div>
        </section>
        <section className="mb-10">
          <h3 className="text-[9px] tracking-[0.2em] uppercase text-zinc-600 font-bold mb-6 px-2">Security & Privacy</h3>
          <div className="flex flex-col border-t border-zinc-800/60">
            <D4_SettingRow icon={<Shield size={14}/>} label="Node Encryption" value="AES-256-GCM"/>
            <D4_SettingToggle label="Biometric Sign" description="Require Auth for Transmit" active={true}/>
            <D4_SettingToggle label="Auto-Wipe" description="Clear buffer after 120s" active={false}/>
          </div>
        </section>
        <section>
          <h3 className="text-[9px] tracking-[0.2em] uppercase text-zinc-600 font-bold mb-6 px-2">Device Status</h3>
          <div className="flex flex-col border-t border-zinc-800/60">
            <D4_SettingRow icon={<Bell size={14}/>} label="Haptic Feedback" value="Industrial"/>
            <div className="py-5 px-2 flex justify-between items-center text-zinc-500"><span className="text-[10px] uppercase tracking-widest font-bold">App Version</span><span className="font-mono text-[11px] text-zinc-600">v2.0.4-beta</span></div>
          </div>
        </section>
      </main>
      <footer className="mt-12 opacity-30"><div className="w-1.5 h-1.5 bg-zinc-500 rounded-full"/></footer>
    </div>
  );

  return (
    <div className="min-h-screen bg-[#0D0D0D] text-[#E0E0E0] font-sans flex flex-col items-center select-none relative pb-24">
      <header className="w-full max-w-md flex flex-col items-center mt-20 mb-16">
        <div className="flex items-center justify-center space-x-[3px] mb-8 h-8">
          {[14,28,14,22,28].map((h,i)=><div key={i} className="w-[3px] bg-white opacity-90" style={{height:`${h}px`}}/>)}
          <div className="w-[3px] h-[14px] bg-white opacity-40 ml-1"/>
        </div>
        <h1 className="text-2xl font-light tracking-[0.5em] text-white uppercase ml-[0.5em]">Kyma</h1>
        <p className="mt-4 text-[9px] tracking-[0.3em] uppercase text-zinc-500 font-bold">Acoustic Cryptography</p>
      </header>
      <main className="w-full max-w-md flex-grow flex flex-col gap-12 px-10">
        <div className="grid grid-cols-2 gap-6 h-40">
          <button className="group flex flex-col items-center justify-center gap-6 p-6 rounded-lg bg-[#151515] border border-zinc-800/40 hover:border-zinc-700 transition-all active:bg-[#1A1A1A]">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" className="w-10 h-10 text-zinc-400 group-hover:text-white transition-colors">
              <circle cx="12" cy="12" r="9"/><path d="M12 15V9M12 9l-2.5 2.5M12 9l2.5 2.5" strokeLinecap="square"/>
            </svg>
            <span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500 group-hover:text-zinc-300">Transmit</span>
          </button>
          <button className="group flex flex-col items-center justify-center gap-6 p-6 rounded-lg bg-[#151515] border border-zinc-800/40 hover:border-zinc-700 transition-all active:bg-[#1A1A1A]">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" className="w-10 h-10 text-zinc-400 group-hover:text-white transition-colors">
              <path d="M6 5h12M12 19v-7M12 19l-3-3M12 19l3-3" strokeLinecap="square"/>
            </svg>
            <span className="text-[10px] font-bold tracking-[0.2em] uppercase text-zinc-500 group-hover:text-zinc-300">Receive</span>
          </button>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <button className="flex items-center justify-center gap-3 py-3.5 rounded-md border border-zinc-800/60 text-zinc-500"><Upload size={14}/><span className="text-[9px] font-bold tracking-[0.15em] uppercase">Backup</span></button>
          <button className="flex items-center justify-center gap-3 py-3.5 rounded-md border border-zinc-800/60 text-zinc-500"><Download size={14}/><span className="text-[9px] font-bold tracking-[0.15em] uppercase">Restore</span></button>
        </div>
      </main>
      <footer className="fixed bottom-12 w-full max-w-md flex flex-col items-center gap-8" style={{zIndex:10}}>
        <div className="flex flex-col items-center gap-2">
          <div className="flex items-center gap-2"><Clock size={10} className="text-zinc-700"/><span className="text-[9px] text-zinc-600 font-bold tracking-[0.2em] uppercase">Last Backup</span></div>
          <span className="text-[10px] text-zinc-400 font-medium">07 MAR 2026 — 14:02</span>
        </div>
        <button onClick={()=>setView('settings')} className="group p-2 text-zinc-600 hover:text-zinc-300 transition-colors"><Settings size={18} strokeWidth={1.5}/></button>
      </footer>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 5 — Static Triangle Home  (Kymaaaaa.md)
// ═══════════════════════════════════════════════════════════════════
function Design5() {
  return (
    <div className="min-h-screen bg-[#0A0A0A] text-zinc-100 font-sans flex flex-col items-center select-none relative pb-20">
      <header className="w-full max-w-md flex flex-col items-center mt-12 mb-10">
        <div className="flex items-end justify-center space-x-1 mb-6 h-8">
          {[1,2,3,4,5].map((_,i)=><div key={i} className="w-1.5 bg-zinc-100 rounded-full" style={{height:`${[12,24,32,20,16][i]}px`,opacity:0.9}}/>)}
        </div>
        <h1 className="text-3xl font-light tracking-[0.35em] ml-2 text-white uppercase">Kyma</h1>
        <div className="flex items-center mt-3 space-x-2 text-zinc-500"><ShieldCheck size={14}/><span className="text-[10px] tracking-widest uppercase font-semibold">Acoustic Cryptography</span></div>
      </header>
      <main className="w-full max-w-md flex-grow flex flex-col gap-8 px-6 sm:px-8">
        <div className="grid grid-cols-2 gap-4 h-48">
          <button className="group flex flex-col items-center justify-center gap-5 p-6 rounded-3xl bg-zinc-900 border border-zinc-800 hover:border-[#00FFA3]/50 hover:bg-[#00FFA3]/5 transition-all active:scale-95">
            <svg viewBox="0 0 24 24" fill="currentColor" className="w-10 h-10 text-zinc-300 group-hover:text-[#00FFA3] transition-colors"><path d="M12 3l10 16H2z"/></svg>
            <span className="text-sm font-medium tracking-wide text-zinc-300 group-hover:text-[#00FFA3] transition-colors">Transmit</span>
          </button>
          <button className="group flex flex-col items-center justify-center gap-5 p-6 rounded-3xl bg-zinc-900 border border-zinc-800 hover:border-[#9945FF]/50 hover:bg-[#9945FF]/5 transition-all active:scale-95">
            <svg viewBox="0 0 24 24" fill="currentColor" className="w-10 h-10 text-zinc-300 group-hover:text-[#9945FF] transition-colors"><path d="M12 21L2 5h20z"/></svg>
            <span className="text-sm font-medium tracking-wide text-zinc-300 group-hover:text-[#9945FF] transition-colors">Receive</span>
          </button>
        </div>
        <div className="flex flex-col gap-4 mt-2">
          <div className="flex items-center gap-4 px-2"><div className="h-px bg-zinc-800 flex-grow"/><span className="text-[10px] uppercase tracking-widest text-zinc-600 font-semibold">Vault Management</span><div className="h-px bg-zinc-800 flex-grow"/></div>
          <div className="grid grid-cols-2 gap-3">
            <button className="flex items-center justify-center gap-2 py-4 rounded-2xl bg-zinc-900/50 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors active:scale-95"><Upload size={18} strokeWidth={2}/><span className="text-sm font-medium tracking-wide">Backup</span></button>
            <button className="flex items-center justify-center gap-2 py-4 rounded-2xl bg-zinc-900/50 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors active:scale-95"><Download size={18} strokeWidth={2}/><span className="text-sm font-medium tracking-wide">Restore</span></button>
          </div>
        </div>
      </main>
      <footer className="fixed bottom-0 w-full max-w-md bg-gradient-to-t from-[#0A0A0A] via-[#0A0A0A] to-transparent pt-8 pb-6 px-6 sm:px-8 flex items-center justify-between" style={{zIndex:10}}>
        <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-zinc-900/60 border border-zinc-800/50 backdrop-blur-sm">
          <Clock size={12} className="text-[#00FFA3]"/>
          <span className="text-xs text-zinc-400 font-medium tracking-wide">Backed up <span className="text-zinc-300">2h ago</span></span>
        </div>
        <button className="p-2.5 rounded-full bg-zinc-900/60 border border-zinc-800/50 text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800 backdrop-blur-sm transition-all active:scale-95"><Settings size={20}/></button>
      </footer>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 6 — Hardware LCD Casing  (Fuck_e.md / gemui2-transmit.md)
// ═══════════════════════════════════════════════════════════════════
const D6_TX_TYPES = [
  { id:'PAY', label:'SOL', unit:'◎', icon:'◎' },
  { id:'SKR', label:'SKR', unit:'SKR', icon:'⬡' },
  { id:'CNFT', label:'NFT', unit:'NFT', icon:'◈' },
  { id:'SIGN', label:'SIG', unit:'SIG', icon:'⬛' },
];

function Design6() {
  const [mode, setMode] = useState('TX');
  const [txType, setTxType] = useState(D6_TX_TYPES[0]);
  const [amount, setAmount] = useState('');
  const [status, setStatus] = useState('IDLE');

  const isIdle = status==='IDLE', isActive=status==='ACTIVE', isSuccess=status==='SUCCESS';
  const needsAmount = txType.id !== 'CNFT' && txType.id !== 'SIGN';

  const triggerAction = () => {
    if (!isIdle) return setStatus('IDLE');
    setStatus('ACTIVE');
    setTimeout(() => { setStatus('SUCCESS'); setTimeout(() => { setStatus('IDLE'); if (mode==='TX') setAmount(''); }, 3000); }, 2500);
  };

  const GrillDots = () => (
    <div className="flex justify-center gap-1.5 mb-6 opacity-20">{[...Array(12)].map((_,i)=><div key={i} className="w-1 h-1 rounded-full bg-black"/>)}</div>
  );

  const D6Wave = ({ activeMode }) => (
    <div className="flex items-end justify-center gap-[3px] h-12 w-full mt-2">
      {[...Array(16)].map((_,i)=>(
        <div key={i} className={`w-1.5 rounded-sm ${activeMode==='TX'?'bg-[#FF3B30]':'bg-[#14F195]'}`}
          style={{ height:'100%', transformOrigin:'bottom',
            animation:`lcd-wave6 ${0.4+Math.random()*0.4}s ease-in-out infinite alternate`,
            animationDelay:`${Math.random()*0.5}s` }}/>
      ))}
      <style>{`@keyframes lcd-wave6{0%{transform:scaleY(0.15);}100%{transform:scaleY(0.95);}}`}</style>
    </div>
  );

  return (
    <div className="min-h-screen bg-[#E5E5E5] flex items-center justify-center p-4 font-sans text-zinc-900 selection:bg-zinc-300">
      <div className="w-full max-w-[380px] bg-[#F2F2F2] rounded-2xl p-6 shadow-[0_30px_60px_-15px_rgba(0,0,0,0.15),inset_0_2px_4px_rgba(255,255,255,0.8)] border border-white/60 relative">
        <GrillDots/>
        <div className="flex justify-between items-end mb-4 px-1">
          <h1 className="text-xl font-bold tracking-[0.2em] text-zinc-800">KYMA</h1>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold tracking-widest text-zinc-400 uppercase">{status}</span>
            <div className={`w-2 h-2 rounded-full transition-colors duration-300 ${isSuccess?'bg-[#14F195] shadow-[0_0_8px_#14F195]':isActive?'bg-[#FF3B30] shadow-[0_0_8px_#FF3B30] animate-pulse':'bg-zinc-300'}`}/>
          </div>
        </div>
        <div className="bg-[#1C1C1A] rounded-xl p-5 shadow-[inset_0_4px_10px_rgba(0,0,0,0.6)] h-44 flex flex-col justify-between relative overflow-hidden mb-8">
          <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[length:100%_4px] pointer-events-none"/>
          <div className="flex justify-between items-start z-10 text-zinc-500 text-xs font-bold tracking-widest uppercase">
            <span>{mode==='TX'?'Transmit':'Listen'}</span><span>{txType.label}</span>
          </div>
          <div className="z-10 flex flex-col items-end w-full">
            {mode==='TX' ? (
              needsAmount ? (
                <div className="flex flex-col items-end w-full">
                  <div className="flex items-baseline gap-2 w-full">
                    <span className={`text-2xl mb-1 transition-colors ${isActive?'text-zinc-700':'text-zinc-600'}`}>{txType.icon}</span>
                    <input type="number" value={amount} onChange={e=>setAmount(e.target.value)} placeholder="0.00" disabled={!isIdle}
                      className={`bg-transparent text-right text-5xl font-light tracking-tighter focus:outline-none w-full placeholder-zinc-700 transition-colors ${isActive?'text-zinc-500':'text-white'}`}/>
                  </div>
                  {isActive && <D6Wave activeMode="TX"/>}
                </div>
              ) : (
                <div className="flex flex-col items-end w-full">
                  <div className="flex items-center gap-3">
                    <ShieldCheck size={32} className={isActive?'text-zinc-700':'text-[#14F195]'}/>
                    <span className={`text-3xl font-light tracking-tighter uppercase transition-colors ${isActive?'text-zinc-500':'text-white'}`}>{txType.id==='SIGN'?'Auth':'1 Asset'}</span>
                  </div>
                  {isActive && <D6Wave activeMode="TX"/>}
                </div>
              )
            ) : (
              <div className="flex flex-col items-end w-full h-full justify-end">
                {isSuccess ? <span className="text-3xl font-light tracking-tighter text-[#14F195]">Payload Rx</span>
                  : isActive ? <div className="w-full flex flex-col items-end"><span className="text-xl font-light tracking-tighter text-[#14F195] mb-2">Scanning...</span><D6Wave activeMode="RX"/></div>
                  : <span className="text-2xl font-light tracking-tighter text-zinc-600">Standby</span>}
              </div>
            )}
          </div>
        </div>
        <div className={`transition-opacity duration-300 ${!isIdle&&mode==='TX'?'opacity-40 pointer-events-none':''}`}>
          <div className="bg-zinc-200 p-1 rounded-full flex relative w-full mb-6 shadow-[inset_0_2px_4px_rgba(0,0,0,0.1)]">
            <div className={`absolute top-1 bottom-1 w-[calc(50%-4px)] bg-white rounded-full shadow-[0_2px_8px_rgba(0,0,0,0.12)] transition-transform duration-300 ${mode==='RX'?'translate-x-full':'translate-x-0'}`}/>
            <button onClick={()=>{setMode('TX');setStatus('IDLE');}} className={`flex-1 relative z-10 py-3 text-[11px] font-bold tracking-[0.15em] transition-colors ${mode==='TX'?'text-zinc-900':'text-zinc-500'}`}>TRANSMIT</button>
            <button onClick={()=>{setMode('RX');setStatus('IDLE');}} className={`flex-1 relative z-10 py-3 text-[11px] font-bold tracking-[0.15em] transition-colors ${mode==='RX'?'text-zinc-900':'text-zinc-500'}`}>RECEIVE</button>
          </div>
          <div className="grid grid-cols-4 gap-2 mb-8">
            {D6_TX_TYPES.map(t => (
              <button key={t.id} onClick={()=>setTxType(t)} disabled={mode==='RX'}
                className={`flex flex-col items-center justify-center p-3 rounded-xl transition-all duration-200 border-b-2 active:border-b-0 active:translate-y-[2px] ${txType.id===t.id&&mode==='TX'?'bg-zinc-800 text-white border-zinc-900 shadow-md':'bg-[#E5E5E5] text-zinc-500 border-[#D1D1D1] hover:bg-[#EAEAEA] disabled:opacity-50'}`}>
                <span className="text-lg mb-1">{t.icon}</span><span className="text-[9px] font-bold tracking-widest">{t.id}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="flex justify-center mt-2 pb-2">
          <button disabled={(!isIdle&&!isActive)||(mode==='TX'&&needsAmount&&!amount)} onClick={triggerAction}
            className={`w-24 h-24 rounded-full flex items-center justify-center transition-all duration-300 border-b-[6px] active:border-b-0 active:translate-y-[6px] ${isSuccess?'bg-[#14F195] border-[#0DAB69] text-zinc-900 shadow-[0_0_20px_rgba(20,241,149,0.4)]':isActive?'bg-zinc-800 border-zinc-900 text-[#FF3B30] shadow-[0_0_20px_rgba(255,59,48,0.2)]':'bg-[#FF3B30] border-[#D0261D] text-white hover:bg-[#FF4A40] disabled:bg-zinc-300 disabled:border-zinc-400 disabled:text-zinc-500'}`}>
            {isSuccess?<Check size={36} strokeWidth={2.5}/>:isActive?<Activity size={36} className={mode==='TX'?'animate-ping':'animate-pulse'} strokeWidth={2}/>:mode==='TX'?<Volume2 size={36} strokeWidth={2} className="ml-1"/>:<Mic size={36} strokeWidth={2}/>}
          </button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 7 — Protocol Dark Sharp  (Kyamaaam.md)
// ═══════════════════════════════════════════════════════════════════
const D7_TX_TYPES = [
  { id:'PAY', label:'SOL Pay', unit:'◎', icon:'◎' },
  { id:'SKR', label:'SKR Tip', unit:'SKR', icon:'⬡' },
  { id:'SIGN', label:'Cold Sign', unit:'SIG', icon:'⬛' },
  { id:'CNFT', label:'cNFT Drop', unit:'NFT', icon:'◈' },
];

function Design7() {
  const [mode, setMode] = useState('TX');
  const [txType, setTxType] = useState(D7_TX_TYPES[0]);
  const [amount, setAmount] = useState('');
  const [recipient, setRecipient] = useState('');
  const [status, setStatus] = useState('IDLE');

  const isIdle=status==='IDLE', isActive=status==='ACTIVE', isSuccess=status==='SUCCESS';
  const triggerAction = () => { setStatus('ACTIVE'); setTimeout(()=>{ setStatus('SUCCESS'); setTimeout(()=>setStatus('IDLE'),3000); },2500); };

  return (
    <div className="min-h-screen bg-[#050505] text-[#EDEDED] font-sans flex justify-center sm:p-6">
      <div className="w-full max-w-md bg-[#0A0A0A] sm:border border-white/10 overflow-hidden flex flex-col relative shadow-2xl">
        <style dangerouslySetInnerHTML={{__html:`@keyframes wave7{0%,100%{transform:scaleY(0.2);}50%{transform:scaleY(1);}} .b7-1{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.0s;} .b7-2{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.1s;} .b7-3{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.2s;} .b7-4{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.3s;} .b7-5{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.2s;} .b7-6{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.1s;} .b7-7{animation:wave7 1.2s ease-in-out infinite;animation-delay:0.0s;}`}}/>
        <div className="flex w-full border-b border-white/10 z-20">
          <button onClick={()=>{setMode('TX');setStatus('IDLE');}} className={`flex-1 py-6 text-sm font-bold tracking-[0.2em] uppercase transition-colors ${mode==='TX'?'bg-white text-black':'bg-[#0A0A0A] text-white/40 hover:text-white'}`}>Transmit</button>
          <button onClick={()=>{setMode('RX');setStatus('IDLE');}} className={`flex-1 py-6 text-sm font-bold tracking-[0.2em] uppercase transition-colors ${mode==='RX'?'bg-white text-black':'bg-[#0A0A0A] text-white/40 hover:text-white'}`}>Receive</button>
        </div>
        <header className="px-6 py-6 flex flex-col items-center z-10">
          <div className="flex items-center gap-2 text-white/40">
            <Activity size={16} className={isActive?"text-[#14F195] animate-pulse":""}/>
            <span className="text-xs font-semibold tracking-[0.3em] uppercase">Kyma Protocol</span>
          </div>
        </header>
        <div className="flex-1 px-6 flex flex-col relative z-10">
          {mode==='TX' ? (
            <div className="flex-1 flex flex-col">
              <div className="flex gap-2 overflow-x-auto pb-4">
                {D7_TX_TYPES.map(t => (
                  <button key={t.id} onClick={()=>setTxType(t)} className={`flex items-center gap-2 px-4 py-2 rounded-none text-sm transition-all whitespace-nowrap border ${txType.id===t.id?'bg-white/10 border-white/20 text-white':'bg-transparent border-transparent text-white/40 hover:text-white/70'}`}>
                    <span className="opacity-50 font-mono">{t.icon}</span>{t.label}
                  </button>
                ))}
              </div>
              <div className="flex-1 flex flex-col justify-center gap-8 mt-4">
                {['SKR','PAY'].includes(txType.id) && (
                  <div className="flex flex-col items-center">
                    <span className="text-xs text-white/40 font-medium tracking-widest uppercase mb-2">Amount</span>
                    <div className="flex items-center gap-3">
                      <span className="text-3xl font-light text-white/30">{txType.icon}</span>
                      <input type="number" placeholder="0.00" value={amount} onChange={e=>setAmount(e.target.value)} className="bg-transparent text-6xl font-light outline-none w-full max-w-[200px] text-center placeholder:text-white/10" disabled={!isIdle}/>
                    </div>
                  </div>
                )}
                {txType.id==='CNFT' && (
                  <div className="flex flex-col items-center">
                    <span className="text-xs text-white/40 font-medium tracking-widest uppercase mb-2">Event Drop ID</span>
                    <input type="text" placeholder="e.g. MONOLITH_2026" className="bg-transparent text-3xl font-light outline-none w-full text-center placeholder:text-white/10 font-mono" disabled={!isIdle}/>
                  </div>
                )}
                {['SKR','PAY','SIGN'].includes(txType.id) && (
                  <div className="w-full relative group">
                    <input type="text" value={recipient} onChange={e=>setRecipient(e.target.value)} placeholder={txType.id==='SIGN'?"Payload Hash":"Recipient Address"}
                      className="w-full bg-transparent border-b border-white/10 py-4 text-sm font-mono placeholder:text-white/20 focus:border-white outline-none transition-colors pr-10" disabled={!isIdle}/>
                    {recipient&&isIdle && <button onClick={()=>setRecipient('')} className="absolute right-0 top-1/2 -translate-y-1/2 text-white/20 hover:text-white transition-colors p-2"><X size={14}/></button>}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center relative">
              <div className="absolute inset-0 bg-gradient-to-b from-transparent via-[#14F195]/5 to-transparent opacity-50 pointer-events-none"/>
              <div className="text-center z-10 flex flex-col items-center">
                <Volume2 size={32} className={`mb-6 ${isActive?'text-[#14F195]':'text-white/20'}`}/>
                <h2 className="text-xl font-light mb-2">{isSuccess?'Payload Received':isActive?'Receiving Signal...':'Listening'}</h2>
                <p className="text-sm text-white/40 max-w-[240px] leading-relaxed">{isSuccess?'Transaction parsed successfully.':'Hold near the transmitting device. Ultrasonic acoustic channel open.'}</p>
              </div>
            </div>
          )}
          <div className="h-24 w-full flex items-center justify-center gap-1.5 my-6">
            {[1,2,3,4,5,6,7].map(b=>(
              <div key={b} className={`w-1.5 rounded-full transition-all duration-300 ${isActive?`bg-[#14F195] b7-${b}`:isSuccess?'bg-white h-full':'bg-white/10 h-1'}`}
                style={isActive?{height:`${[40,60,80,100,80,60,40][b-1]}%`}:{}}/>
            ))}
          </div>
        </div>
        <div className="p-6 pt-0 z-10">
          <button onClick={triggerAction} disabled={!isIdle||(mode==='TX'&&!amount&&txType.id!=='CNFT'&&txType.id!=='SIGN')}
            className={`w-full py-5 rounded-none flex items-center justify-center gap-3 text-sm font-bold tracking-[0.2em] uppercase transition-all duration-300 relative overflow-hidden group ${isSuccess?'bg-white text-black':isActive?'bg-[#14F195]/10 text-[#14F195] border border-[#14F195]/30':'bg-white text-black disabled:bg-white/5 disabled:text-white/20'}`}>
            {isIdle&&<div className="absolute inset-0 bg-black/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300"/>}
            <span className="relative z-10 flex items-center gap-2">
              {isSuccess?<><Check size={18}/> {mode==='TX'?'Sent':'Decoded'}</>
                :isActive?<>{mode==='TX'?<Volume2 size={18} className="animate-pulse"/>:<Mic size={18} className="animate-pulse"/>} {mode==='TX'?'Transmitting':'Listening'}</>
                :<>{mode==='TX'?<Send size={18}/>:<ArrowDownToLine size={18}/>} {mode==='TX'?'Initiate':'Start Listener'}</>}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 8 — Protocol Dark Rounded  (Kymaagainnn.md)
// ═══════════════════════════════════════════════════════════════════
const D8_TX_TYPES = [
  { id:'SKR', label:'SKR Tip', unit:'SKR', icon:'⬡' },
  { id:'PAY', label:'SOL Pay', unit:'◎', icon:'◎' },
  { id:'CNFT', label:'cNFT Drop', unit:'NFT', icon:'◈' },
  { id:'SIGN', label:'Cold Sign', unit:'SIG', icon:'⬛' },
];

function Design8() {
  const [mode, setMode] = useState('TX');
  const [txType, setTxType] = useState(D8_TX_TYPES[0]);
  const [amount, setAmount] = useState('');
  const [recipient, setRecipient] = useState('');
  const [status, setStatus] = useState('IDLE');

  const isIdle=status==='IDLE', isActive=status==='ACTIVE', isSuccess=status==='SUCCESS';
  const triggerAction = () => { setStatus('ACTIVE'); setTimeout(()=>{ setStatus('SUCCESS'); setTimeout(()=>setStatus('IDLE'),3000); },2500); };

  return (
    <div className="min-h-screen bg-[#050505] text-[#EDEDED] font-sans flex justify-center sm:p-6">
      <div className="w-full max-w-md bg-[#0A0A0A] sm:rounded-3xl sm:border border-white/10 overflow-hidden flex flex-col relative shadow-2xl">
        <style dangerouslySetInnerHTML={{__html:`@keyframes wave8{0%,100%{transform:scaleY(0.2);}50%{transform:scaleY(1);}} .b8-1{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.0s;} .b8-2{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.1s;} .b8-3{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.2s;} .b8-4{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.3s;} .b8-5{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.2s;} .b8-6{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.1s;} .b8-7{animation:wave8 1.2s ease-in-out infinite;animation-delay:0.0s;}`}}/>
        <header className="px-6 py-8 flex flex-col items-center z-10">
          <div className="flex items-center gap-2 text-white/40 mb-6">
            <Activity size={16} className={isActive?"text-[#14F195] animate-pulse":""}/>
            <span className="text-xs font-semibold tracking-[0.3em] uppercase">Kyma Protocol</span>
          </div>
          <div className="bg-white/5 p-1 rounded-full flex w-full max-w-[240px] relative">
            <button onClick={()=>{setMode('TX');setStatus('IDLE');}} className={`flex-1 py-2.5 text-xs font-semibold tracking-wider uppercase rounded-full transition-all duration-300 z-10 ${mode==='TX'?'text-black':'text-white/40 hover:text-white'}`}>Transmit</button>
            <button onClick={()=>{setMode('RX');setStatus('IDLE');}} className={`flex-1 py-2.5 text-xs font-semibold tracking-wider uppercase rounded-full transition-all duration-300 z-10 ${mode==='RX'?'text-black':'text-white/40 hover:text-white'}`}>Receive</button>
            <div className="absolute top-1 bottom-1 w-[calc(50%-4px)] bg-white rounded-full transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)]" style={{transform:mode==='TX'?'translateX(0)':'translateX(100%)'}}/>
          </div>
        </header>
        <div className="flex-1 px-6 flex flex-col relative z-10">
          {mode==='TX' ? (
            <div className="flex-1 flex flex-col">
              <div className="flex gap-2 overflow-x-auto pb-4">
                {D8_TX_TYPES.map(t => (
                  <button key={t.id} onClick={()=>setTxType(t)} className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm transition-all whitespace-nowrap border ${txType.id===t.id?'bg-white/10 border-white/20 text-white':'bg-transparent border-transparent text-white/40 hover:text-white/70'}`}>
                    <span className="opacity-50 font-mono">{t.icon}</span>{t.label}
                  </button>
                ))}
              </div>
              <div className="flex-1 flex flex-col justify-center gap-8 mt-4">
                {['SKR','PAY'].includes(txType.id) && (
                  <div className="flex flex-col items-center">
                    <span className="text-xs text-white/40 font-medium tracking-widest uppercase mb-2">Amount</span>
                    <div className="flex items-center gap-3">
                      <span className="text-3xl font-light text-white/30">{txType.icon}</span>
                      <input type="number" placeholder="0.00" value={amount} onChange={e=>setAmount(e.target.value)} className="bg-transparent text-6xl font-light outline-none w-full max-w-[200px] text-center placeholder:text-white/10" disabled={!isIdle}/>
                    </div>
                  </div>
                )}
                {txType.id==='CNFT' && (
                  <div className="flex flex-col items-center">
                    <span className="text-xs text-white/40 font-medium tracking-widest uppercase mb-2">Event Drop ID</span>
                    <input type="text" placeholder="e.g. MONOLITH_2026" className="bg-transparent text-3xl font-light outline-none w-full text-center placeholder:text-white/10 font-mono" disabled={!isIdle}/>
                  </div>
                )}
                {['SKR','PAY','SIGN'].includes(txType.id) && (
                  <div className="w-full relative group">
                    <input type="text" value={recipient} onChange={e=>setRecipient(e.target.value)} placeholder={txType.id==='SIGN'?"Payload Hash":"Recipient Address"}
                      className="w-full bg-transparent border-b border-white/10 py-4 text-sm font-mono placeholder:text-white/20 focus:border-white outline-none transition-colors pr-10" disabled={!isIdle}/>
                    {recipient&&isIdle&&<button onClick={()=>setRecipient('')} className="absolute right-0 top-1/2 -translate-y-1/2 text-white/20 hover:text-white transition-colors p-2"><X size={14}/></button>}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center relative">
              <div className="absolute inset-0 bg-gradient-to-b from-transparent via-[#14F195]/5 to-transparent opacity-50 pointer-events-none"/>
              <div className="text-center z-10 flex flex-col items-center">
                <Volume2 size={32} className={`mb-6 ${isActive?'text-[#14F195]':'text-white/20'}`}/>
                <h2 className="text-xl font-light mb-2">{isSuccess?'Payload Received':isActive?'Receiving Signal...':'Listening'}</h2>
                <p className="text-sm text-white/40 max-w-[240px] leading-relaxed">{isSuccess?'Transaction parsed successfully.':'Hold near the transmitting device. Ultrasonic acoustic channel open.'}</p>
              </div>
            </div>
          )}
          <div className="h-24 w-full flex items-center justify-center gap-1.5 my-6">
            {[1,2,3,4,5,6,7].map(b=>(
              <div key={b} className={`w-1.5 rounded-full transition-all duration-300 ${isActive?`bg-[#14F195] b8-${b}`:isSuccess?'bg-white h-full':'bg-white/10 h-1'}`}
                style={isActive?{height:`${[40,60,80,100,80,60,40][b-1]}%`}:{}}/>
            ))}
          </div>
        </div>
        <div className="p-6 pt-0 z-10">
          <button onClick={triggerAction} disabled={!isIdle||(mode==='TX'&&!amount&&txType.id!=='CNFT'&&txType.id!=='SIGN')}
            className={`w-full py-5 rounded-2xl flex items-center justify-center gap-3 text-sm font-semibold tracking-wider uppercase transition-all duration-300 relative overflow-hidden group ${isSuccess?'bg-white text-black':isActive?'bg-[#14F195]/10 text-[#14F195] border border-[#14F195]/30':'bg-white text-black disabled:bg-white/5 disabled:text-white/20'}`}>
            {isIdle&&<div className="absolute inset-0 bg-black/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300"/>}
            <span className="relative z-10 flex items-center gap-2">
              {isSuccess?<><Check size={18}/> {mode==='TX'?'Sent':'Decoded'}</>
                :isActive?<>{mode==='TX'?<Volume2 size={18} className="animate-pulse"/>:<Mic size={18} className="animate-pulse"/>} {mode==='TX'?'Transmitting':'Listening'}</>
                :<>{mode==='TX'?<Send size={18}/>:<ArrowDownToLine size={18}/>} {mode==='TX'?'Initiate':'Start Listener'}</>}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN 9 — Advanced Protocol v2.0  (kyma-ui.jsx)
// ═══════════════════════════════════════════════════════════════════
const ENVELOPE=104, FRAME_MS=21.33, PRE_FRAMES=16, MAX_BURST=140;
const D9_TX_TYPES = [
  { id:"SKR",  icon:"⬡", short:"SKR",  label:"SKR TIP",   ctext:40,  fields:["to","amt"],       desc:"Peer-to-peer SKR transfer" },
  { id:"CNFT", icon:"◈", short:"cNFT", label:"cNFT DROP", ctext:8,   fields:["eid"],             desc:"Broadcast NFT to all in range" },
  { id:"PAY",  icon:"◎", short:"PAY",  label:"SOL PAY",   ctext:73,  fields:["to","amt","memo"], desc:"Acoustic Solana Pay request" },
  { id:"SIGN", icon:"⬛", short:"SIGN", label:"COLD SIGN", ctext:265, fields:["tx"],              desc:"⚠ Multi-burst · 3 bursts · ~13.2s" },
];
const D9 = { bg:"#07070F", surf:"#0D0D1A", el:"#131325", bord:"#181830", t1:"#CDD4F0", t2:"#3E3E60", t3:"#252540", mono:'"JetBrains Mono","Courier New",monospace', sans:'"Work Sans",system-ui,sans-serif' };
function calcPayload(id) {
  const t = D9_TX_TYPES.find(x=>x.id===id)??D9_TX_TYPES[0];
  const total=t.ctext+ENVELOPE, ecc=total<=32?16:32, groups=Math.ceil((total+ecc)/3), ms=(groups*3+PRE_FRAMES*2)*FRAME_MS;
  return { total, sec:(ms/1000).toFixed(2), bursts:Math.ceil(total/MAX_BURST) };
}
const rh=(n=8)=>Array.from(crypto.getRandomValues(new Uint8Array(n))).map(b=>b.toString(16).padStart(2,"0")).join("").toUpperCase();
const TREE_ADDR="BGUMAp9G···aRPUY";

function D9_FreqBand({ active, accent }) {
  const N=32;
  const [bars, setBars] = useState(()=>Array(N).fill(0).map(()=>1+Math.random()*3));
  const fr = useRef(0);
  useEffect(() => {
    const TICK=active?50:130;
    const id=setInterval(()=>{
      fr.current++;
      setBars(()=>{
        const b=Array(N).fill(0).map(()=>1+Math.random()*3);
        if(active){
          const offset=Math.round(((Math.sin(fr.current*0.32)*0.5+0.5)*(N-8)));
          for(let i=0;i<6;i++) b[Math.min(N-1,offset+i)]=48+Math.random()*52;
          if(Math.random()>0.52){const o2=Math.floor(Math.random()*(N-6));for(let i=0;i<6;i++) b[o2+i]=Math.max(b[o2+i],20+Math.random()*38);}
        }
        return b;
      });
    },TICK);
    return ()=>clearInterval(id);
  },[active]);
  return (
    <div style={{background:"#050509",borderBottom:`1px solid ${D9.bord}`,padding:"9px 12px 5px"}}>
      <div style={{display:"flex",alignItems:"flex-end",height:52,gap:1}}>
        {bars.map((h,i)=><div key={i} style={{flex:1,height:`${Math.max(1.5,Math.min(100,h))}%`,background:h>18?accent:accent+"15",boxShadow:h>50?`0 0 5px ${accent}55`:"none",transition:`height ${active?50:130}ms linear`}}/>)}
      </div>
      <div style={{display:"flex",justifyContent:"space-between",paddingTop:4,fontFamily:D9.mono,fontSize:8,color:D9.t3,letterSpacing:"0.12em"}}>
        <span>15.0 kHz</span><span style={{color:D9.t3+"BB"}}>KYMA ACOUSTIC BAND — mFSK</span><span>19.5 kHz</span>
      </div>
    </div>
  );
}

function D9_LogStream({ logs, accent }) {
  const end=useRef(null);
  useEffect(()=>{end.current?.scrollIntoView({behavior:"smooth"});},[logs]);
  return (
    <div style={{background:"#040408",border:`1px solid ${D9.bord}`,padding:"8px 10px",height:90,overflowY:"auto",marginTop:10}}>
      {logs.length===0?<div style={{fontFamily:D9.mono,fontSize:9,color:D9.t3,letterSpacing:"0.1em"}}>—</div>
        :logs.map((l,i)=><div key={i} style={{fontFamily:D9.mono,fontSize:9.5,letterSpacing:"0.08em",color:i===logs.length-1?accent:"#2A2A48",lineHeight:1.75}}>{l}</div>)}
      <div ref={end}/>
    </div>
  );
}

function Design9() {
  const [mode,setMode]=useState("TX");
  const [txType,setTxType]=useState("SKR");
  const [txSt,setTxSt]=useState("IDLE");
  const [rxSt,setRxSt]=useState("WAIT");
  const [to,setTo]=useState("");
  const [amt,setAmt]=useState("");
  const [memo,setMemo]=useState("");
  const [txData,setTxData]=useState("");
  const [eid]=useState(()=>rh(4));
  const [logs,setLogs]=useState([]);
  const [prog,setProg]=useState(0);
  const [hash,setHash]=useState("");
  const [decoded,setDecoded]=useState(null);

  const accent=mode==="TX"?"#F0A500":"#0CD4E8";
  const p=calcPayload(txType);
  const waveON=(mode==="TX"&&(txSt==="TX"||txSt==="SEAL"))||(mode==="RX"&&(rxSt==="DET"||rxSt==="DEC"));
  const log=useCallback(m=>setLogs(prev=>[...prev.slice(-8),"> "+m]),[]);

  useEffect(()=>{
    if(txSt==="SEAL"){
      setProg(0);log("CONNECTING_SEED_VAULT");
      const t=setTimeout(()=>{log("BIOMETRIC_VERIFIED");log("PAYLOAD_SEALED");setTxSt("TX");},1800);
      return()=>clearTimeout(t);
    }
    if(txSt==="TX"){
      log("ENCODING_mFSK…");log(`PAYLOAD:${p.total}B  ECC:32B  BURSTS:${p.bursts}`);
      const totalMs=parseFloat(p.sec)*1000;let el=0;
      const iv=setInterval(()=>{el+=40;setProg(Math.min(100,(el/totalMs)*100));if(el>=totalMs){clearInterval(iv);log("TX_COMPLETE");log("BLOCK_CONFIRMED");setHash(rh(16));setTxSt("DONE");}},40);
      return()=>clearInterval(iv);
    }
    if(txSt==="DONE"){const t=setTimeout(()=>{setTxSt("IDLE");setLogs([]);setProg(0);},5500);return()=>clearTimeout(t);}
  },[txSt]);

  useEffect(()=>{
    if(mode!=="RX") return;
    if(rxSt==="DET"){
      log("PREAMBLE_DETECTED");
      const t=setTimeout(()=>{log("FREQ_LOCK: 15.4kHz");log("RS_ECC_ACTIVE");setRxSt("DEC");},1300);
      return()=>clearTimeout(t);
    }
    if(rxSt==="DEC"){
      let pc=0;
      const iv=setInterval(()=>{pc+=7;setProg(Math.min(100,pc));if(pc>=100){clearInterval(iv);log("GCM_TAG_VALID");log("TTL: 28.4s remaining");log("PAYLOAD_DECRYPTED");setDecoded({type:"SKR TIP",from:rh(7)+"···",amt:(Math.random()*4+0.1).toFixed(4),bytes:144,ttl:"28.4s"});setRxSt("REVIEW");}},100);
      return()=>clearInterval(iv);
    }
    if(rxSt==="SIGN"){
      log("SEED_VAULT_SIGNING…");
      const t=setTimeout(()=>{log("SIGNATURE_GENERATED");log("BROADCASTING_TX…");setHash(rh(16));setTimeout(()=>setRxSt("DONE"),1100);},2000);
      return()=>clearTimeout(t);
    }
    if(rxSt==="DONE"){const t=setTimeout(()=>{setRxSt("WAIT");setDecoded(null);setLogs([]);setProg(0);},5500);return()=>clearTimeout(t);}
  },[rxSt,mode]);

  const txStart=()=>{if(txSt==="IDLE"){setLogs([]);setProg(0);setTxSt("SEAL");}};
  const rxReject=()=>{log("TX_REJECTED_BY_USER");setDecoded(null);setLogs([]);setProg(0);setRxSt("WAIT");};
  const canTX=()=>{
    if(txSt!=="IDLE") return false;
    const t=D9_TX_TYPES.find(x=>x.id===txType);if(!t) return false;
    if(t.fields.includes("to")&&to.length<8) return false;
    if(t.fields.includes("amt")&&!parseFloat(amt)) return false;
    if(t.fields.includes("tx")&&txData.length<4) return false;
    return true;
  };

  const Lb=({c})=><div style={{fontFamily:D9.mono,fontSize:9,letterSpacing:"0.18em",color:D9.t2,marginBottom:5,textTransform:"uppercase"}}>{c}</div>;
  const FI=({val,set,ph,type="text",sm,rows})=>{
    const s={width:"100%",boxSizing:"border-box",background:"#090914",border:`1px solid ${val?accent+"45":D9.bord}`,color:val?D9.t1:"#282845",fontFamily:D9.mono,fontSize:sm?11:12,padding:"9px 11px",outline:"none",resize:"none",letterSpacing:"0.04em",transition:"border-color 0.2s",lineHeight:1.5};
    if(rows) return <textarea value={val} onChange={e=>set(e.target.value)} placeholder={ph} rows={rows} style={s}/>;
    return <input type={type} value={val} onChange={e=>set(e.target.value)} placeholder={ph} style={s}/>;
  };
  const RO=({val})=><div style={{fontFamily:D9.mono,fontSize:val.length>16?10:13,color:accent,letterSpacing:"0.12em",padding:"9px 11px",background:"#090914",border:`1px solid ${accent}35`}}>{val}</div>;
  const Mu=({val})=><div style={{fontFamily:D9.mono,fontSize:11,color:D9.t2,padding:"9px 11px",background:"#090914",border:`1px solid ${D9.bord}`}}>{val}</div>;
  const PB=({val,label})=>(
    <div style={{paddingBottom:10}}>
      <div style={{display:"flex",justifyContent:"space-between",marginBottom:5}}>
        <span style={{fontFamily:D9.mono,fontSize:9,letterSpacing:"0.12em",color:D9.t2,textTransform:"uppercase"}}>{label}</span>
        <span style={{fontFamily:D9.mono,fontSize:9,color:accent}}>{Math.round(val)}%</span>
      </div>
      <div style={{height:2,background:D9.el,overflow:"hidden"}}>
        <div style={{height:"100%",width:`${val}%`,background:accent,boxShadow:`0 0 8px ${accent}80`,transition:"width 40ms linear"}}/>
      </div>
    </div>
  );
  const PayCard=()=>(
    <div style={{background:D9.el,border:`1px solid ${D9.bord}`,padding:"9px 12px",marginTop:14}}>
      <div style={{display:"flex",justifyContent:"space-between",marginBottom:7}}>
        <span style={{fontFamily:D9.mono,fontSize:8,letterSpacing:"0.18em",color:D9.t2,textTransform:"uppercase"}}>PAYLOAD ESTIMATE</span>
        <span style={{fontFamily:D9.mono,fontSize:8,letterSpacing:"0.12em",color:accent}}>{p.bursts>1?`${p.bursts} BURSTS`:"1 BURST"}</span>
      </div>
      <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:0}}>
        {[["BYTES",`${p.total}B`],["TIME",`~${p.sec}s`],["PROTOCOL","U-FAST"],["ECC","RS-32"]].map(([k,v])=>(
          <div key={k} style={{paddingRight:8}}><div style={{fontFamily:D9.mono,fontSize:7.5,color:D9.t2,letterSpacing:"0.1em"}}>{k}</div><div style={{fontFamily:D9.mono,fontSize:12,color:D9.t1,marginTop:3,letterSpacing:"0.06em"}}>{v}</div></div>
        ))}
      </div>
    </div>
  );
  const VA=({label="AWAITING BIOMETRIC"})=>(
    <div style={{flex:1,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:20,paddingTop:10}}>
      <div style={{position:"relative",width:96,height:96}}>
        {[46,62,80].map((sz,i)=><div key={i} style={{position:"absolute",top:"50%",left:"50%",width:sz,height:sz,marginLeft:-sz/2,marginTop:-sz/2,borderRadius:"50%",border:`1px solid ${accent}`,opacity:0,animation:`kyma9-pulse 2.4s ease-out ${i*0.7}s infinite`}}/>)}
        <div style={{position:"absolute",inset:18,borderRadius:"50%",background:accent+"12",border:`1px solid ${accent}40`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:28}}>🔐</div>
      </div>
      <div style={{textAlign:"center"}}>
        <div style={{fontFamily:D9.sans,fontWeight:700,fontSize:10,letterSpacing:"0.28em",color:accent,textTransform:"uppercase"}}>SEED VAULT TEE</div>
        <div style={{fontFamily:D9.mono,fontSize:10,letterSpacing:"0.15em",color:D9.t2,marginTop:8}}>{label}</div>
        <div style={{fontFamily:D9.mono,fontSize:8,letterSpacing:"0.1em",color:D9.t2+"60",marginTop:5}}>DOUBLE-TAP POWER BUTTON</div>
      </div>
    </div>
  );
  const CS=({verb="TX CONFIRMED"})=>(
    <div style={{flex:1,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:14,background:accent+"0D",padding:24}}>
      <div style={{fontSize:52,color:accent,lineHeight:1}}>✓</div>
      <div style={{fontFamily:D9.sans,fontWeight:800,fontSize:13,letterSpacing:"0.3em",color:accent,textTransform:"uppercase"}}>{verb}</div>
      <div style={{height:1,width:"100%",background:D9.bord}}/>
      <div style={{width:"100%"}}><Lb c="Transaction Hash"/><div style={{fontFamily:D9.mono,fontSize:9.5,color:D9.t1,letterSpacing:"0.06em",wordBreak:"break-all"}}>{hash.slice(0,22)}···</div></div>
      <div style={{fontFamily:D9.mono,fontSize:8,color:D9.t2,letterSpacing:"0.12em"}}>RESETS IN 5s</div>
    </div>
  );

  const type=D9_TX_TYPES.find(t=>t.id===txType);
  const btnLabels={IDLE:"[ TRANSMIT ]",SEAL:"[ SEALING… ]",TX:"[ TRANSMITTING… ]",DONE:"[ CONFIRMED ]"};

  function renderTX(){
    if(txSt==="DONE") return <CS/>;
    if(txSt==="SEAL") return <VA/>;
    if(txSt==="TX") return (
      <div style={{flex:1,paddingBottom:8}}>
        <div style={{fontFamily:D9.sans,fontWeight:600,fontSize:10,letterSpacing:"0.25em",color:accent,textTransform:"uppercase",marginBottom:14}}>TRANSMITTING</div>
        <PB val={prog} label={`BURST 1 / ${p.bursts}  ·  ${p.sec}s TOTAL`}/>
        <D9_LogStream logs={logs} accent={accent}/>
      </div>
    );
    return (
      <div style={{flex:1}}>
        <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:6,marginBottom:16}}>
          {D9_TX_TYPES.map(t=>(
            <button key={t.id} onClick={()=>setTxType(t.id)} style={{background:txType===t.id?accent+"18":D9.el,border:`1px solid ${txType===t.id?accent:D9.bord}`,color:txType===t.id?accent:D9.t2,fontFamily:D9.mono,fontSize:9,letterSpacing:"0.1em",padding:"8px 2px",cursor:"pointer",textAlign:"center",transition:"all 0.15s"}}>
              <div style={{fontSize:15,marginBottom:3}}>{t.icon}</div>
              <div style={{textTransform:"uppercase"}}>{t.short}</div>
            </button>
          ))}
        </div>
        <div style={{fontFamily:D9.mono,fontSize:9.5,color:D9.t2,marginBottom:14,letterSpacing:"0.05em"}}>{type.desc}</div>
        {type.fields.includes("to")&&<div style={{marginBottom:12}}><Lb c="Recipient Wallet"/><FI val={to} set={setTo} ph="Enter Solana wallet address (base58)"/></div>}
        {type.fields.includes("amt")&&<div style={{marginBottom:12}}><Lb c="Amount (SKR)"/><FI val={amt} set={setAmt} ph="0.001" type="number"/></div>}
        {type.fields.includes("memo")&&<div style={{marginBottom:12}}><Lb c="Memo (optional)"/><FI val={memo} set={setMemo} ph="Payment reference" sm/></div>}
        {type.fields.includes("eid")&&<><div style={{marginBottom:12}}><Lb c="Event ID (auto-generated)"/><RO val={eid}/></div><div style={{marginBottom:12}}><Lb c="cNFT Tree (mainnet)"/><Mu val={TREE_ADDR}/></div><div style={{fontFamily:D9.mono,fontSize:9,color:D9.t2+"90",letterSpacing:"0.08em",marginBottom:12}}>↳ Broadcasts to ALL Seekers in range simultaneously</div></>}
        {type.fields.includes("tx")&&<div style={{marginBottom:12}}><Lb c="Unsigned Transaction (base64)"/><FI val={txData} set={setTxData} ph="Paste base64-encoded unsigned transaction…" rows={4}/>{txData&&<div style={{fontFamily:D9.mono,fontSize:8.5,color:"#F97316",marginTop:5,letterSpacing:"0.08em"}}>⚠ 3-burst transmission · ~13.2s · patched JNI recommended</div>}</div>}
        <PayCard/>
      </div>
    );
  }

  function renderRX(){
    if(rxSt==="DONE") return <CS verb="TX RECEIVED"/>;
    if(rxSt==="SIGN") return <VA label="SIGNING TRANSACTION"/>;
    if(rxSt==="WAIT") return (
      <div style={{flex:1,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:22,padding:"20px 0"}}>
        <div style={{position:"relative",width:108,height:108}}>
          {[56,76,96].map((sz,i)=><div key={i} style={{position:"absolute",top:"50%",left:"50%",width:sz,height:sz,marginLeft:-sz/2,marginTop:-sz/2,borderRadius:"50%",border:`1px solid ${accent}30`,opacity:0,animation:`kyma9-pulse 3.2s ease-out ${i*1.0}s infinite`}}/>)}
          <div style={{position:"absolute",inset:22,borderRadius:"50%",background:accent+"0C",border:`1px solid ${accent}28`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:30}}>📡</div>
        </div>
        <div style={{textAlign:"center"}}>
          <div style={{fontFamily:D9.sans,fontWeight:600,fontSize:11,letterSpacing:"0.22em",color:accent,textTransform:"uppercase"}}>ACOUSTIC RECEIVER</div>
          <div style={{fontFamily:D9.mono,fontSize:10,color:D9.t2,marginTop:9,letterSpacing:"0.12em",animation:"kyma9-blink 1.6s step-end infinite"}}>AWAITING KYMA SIGNAL…</div>
          <div style={{fontFamily:D9.mono,fontSize:8,color:D9.t3,marginTop:6,letterSpacing:"0.1em"}}>15,000 – 19,453 Hz · AES-256-GCM · TTL 30s</div>
        </div>
        <button onClick={()=>{setLogs([]);setProg(0);setRxSt("DET");}} style={{marginTop:8,background:"transparent",border:`1px solid ${D9.bord}`,color:D9.t2,fontFamily:D9.mono,fontSize:8,letterSpacing:"0.18em",padding:"7px 16px",cursor:"pointer",textTransform:"uppercase"}}>[ SIMULATE SIGNAL ]</button>
      </div>
    );
    if(rxSt==="DET"||rxSt==="DEC") return (
      <div style={{flex:1,paddingBottom:8}}>
        <div style={{fontFamily:D9.sans,fontWeight:600,fontSize:10,letterSpacing:"0.25em",color:accent,textTransform:"uppercase",marginBottom:14}}>{rxSt==="DET"?"SIGNAL DETECTED":"DECODING PAYLOAD"}</div>
        <PB val={prog} label={rxSt==="DEC"?"RS-ECC CORRECTION → AES-GCM":"FREQUENCY LOCK"}/>
        <D9_LogStream logs={logs} accent={accent}/>
      </div>
    );
    if(rxSt==="REVIEW"&&decoded) return (
      <div style={{flex:1,paddingBottom:8}}>
        <div style={{fontFamily:D9.sans,fontWeight:600,fontSize:10,letterSpacing:"0.25em",color:accent,textTransform:"uppercase",marginBottom:12}}>PAYLOAD DECODED</div>
        <div style={{border:`1px solid ${accent}35`,background:D9.el}}>
          {[["TYPE",decoded.type],["FROM",decoded.from],["AMOUNT",decoded.amt+" SKR"],["TO","This wallet"],["BYTES",decoded.bytes+"B"],["TTL",decoded.ttl+" remaining"]].map(([k,v],i)=>(
            <div key={k} style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"8px 12px",borderBottom:i<5?`1px solid ${D9.bord}`:"none"}}>
              <span style={{fontFamily:D9.mono,fontSize:8.5,letterSpacing:"0.15em",color:D9.t2,textTransform:"uppercase"}}>{k}</span>
              <span style={{fontFamily:D9.mono,fontSize:11,color:D9.t1}}>{v}</span>
            </div>
          ))}
        </div>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:8,marginTop:14}}>
          <button onClick={rxReject} style={{background:"transparent",border:"1px solid #EF444460",color:"#EF4444",fontFamily:D9.mono,fontSize:10,letterSpacing:"0.15em",padding:"13px",cursor:"pointer",textTransform:"uppercase"}}>[ REJECT ]</button>
          <button onClick={()=>setRxSt("SIGN")} style={{background:accent+"18",border:`1px solid ${accent}`,color:accent,fontFamily:D9.mono,fontSize:10,letterSpacing:"0.15em",padding:"13px",cursor:"pointer",textTransform:"uppercase",fontWeight:700}}>[ APPROVE ]</button>
        </div>
      </div>
    );
    return null;
  }

  return (<>
    <style>{`
      @import url('https://fonts.googleapis.com/css2?family=Work+Sans:wght@400;600;700;800&family=JetBrains+Mono:wght@400;700&display=swap');
      @keyframes kyma9-pulse{0%{opacity:0.75;transform:scale(1);}100%{opacity:0;transform:scale(2.4);}}
      @keyframes kyma9-blink{0%,49%{opacity:1;}50%,100%{opacity:0.2;}}
    `}</style>
    <div style={{minHeight:"100vh",background:"#03030A",display:"flex",alignItems:"center",justifyContent:"center",padding:16,fontFamily:D9.sans}}>
      <div style={{width:375,background:D9.bg,border:`1px solid ${D9.bord}`,boxShadow:`0 0 100px rgba(0,0,0,0.95),0 0 0 1px #0E0E22`,display:"flex",flexDirection:"column",overflow:"hidden",position:"relative"}}>
        {[["top","left"],["top","right"],["bottom","left"],["bottom","right"]].map(([v,h])=>(
          <div key={v+h} style={{position:"absolute",zIndex:60,[v]:-1,[h]:-1,width:14,height:14,borderTop:v==="top"?`2px solid ${accent}65`:"none",borderBottom:v==="bottom"?`2px solid ${accent}65`:"none",borderLeft:h==="left"?`2px solid ${accent}65`:"none",borderRight:h==="right"?`2px solid ${accent}65`:"none",pointerEvents:"none"}}/>
        ))}
        <div style={{position:"absolute",inset:0,zIndex:50,pointerEvents:"none",backgroundImage:"repeating-linear-gradient(0deg,transparent,transparent 2px,rgba(0,0,0,0.03) 2px,rgba(0,0,0,0.03) 4px)"}}/>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"10px 14px",borderBottom:`1px solid ${D9.bord}`,background:D9.surf,zIndex:10}}>
          <div style={{display:"flex",alignItems:"center",gap:10}}>
            <span style={{fontFamily:D9.mono,fontSize:12,letterSpacing:"0.32em",color:accent,fontWeight:700}}>KYMA</span>
            <span style={{fontFamily:D9.mono,fontSize:8,letterSpacing:"0.15em",color:D9.t2}}>v2.0</span>
          </div>
          <div style={{display:"flex",alignItems:"center",gap:7}}>
            <div style={{width:5,height:5,borderRadius:"50%",background:waveON?accent:D9.t3,boxShadow:waveON?`0 0 6px ${accent}`:"none",transition:"all 0.3s"}}/>
            <span style={{fontFamily:D9.mono,fontSize:8.5,letterSpacing:"0.16em",color:waveON?accent:D9.t2}}>{mode==="TX"?txSt:rxSt}</span>
          </div>
        </div>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",background:D9.surf,borderBottom:`1px solid ${D9.bord}`,zIndex:10}}>
          {[{id:"TX",icon:"▶",lbl:"TRANSMIT"},{id:"RX",icon:"◀",lbl:"RECEIVE"}].map(m=>(
            <button key={m.id} onClick={()=>{setMode(m.id);setLogs([]);setProg(0);}} style={{padding:"11px 8px",background:mode===m.id?accent+"15":"transparent",border:"none",borderBottom:`2px solid ${mode===m.id?accent:"transparent"}`,color:mode===m.id?accent:D9.t2,fontFamily:D9.mono,fontSize:9,letterSpacing:"0.22em",fontWeight:mode===m.id?700:400,cursor:"pointer",transition:"all 0.15s",textTransform:"uppercase"}}>
              {m.icon} {m.lbl}
            </button>
          ))}
        </div>
        <D9_FreqBand active={waveON} accent={accent}/>
        <div style={{flex:1,overflowY:"auto",padding:"14px",display:"flex",flexDirection:"column",minHeight:360,maxHeight:460}}>
          {mode==="TX"?renderTX():renderRX()}
        </div>
        {mode==="TX"&&(
          <div style={{padding:"8px 14px 14px",background:D9.surf,borderTop:`1px solid ${D9.bord}`,zIndex:10}}>
            <button onClick={txStart} disabled={!canTX()} style={{width:"100%",padding:"14px 0",background:canTX()&&txSt==="IDLE"?accent+"12":"transparent",border:`1.5px solid ${canTX()&&txSt==="IDLE"?accent:D9.bord}`,color:canTX()&&txSt==="IDLE"?accent:D9.t2,fontFamily:D9.mono,fontSize:10,letterSpacing:"0.25em",fontWeight:700,cursor:canTX()&&txSt==="IDLE"?"pointer":"not-allowed",textTransform:"uppercase",transition:"all 0.15s"}}>{btnLabels[txSt]??"[ TRANSMIT ]"}</button>
          </div>
        )}
      </div>
    </div>
  </>);
}

// ═══════════════════════════════════════════════════════════════════
// DESIGN BROWSER WRAPPER
// ═══════════════════════════════════════════════════════════════════
const DESIGNS = [
  { name: "Full App — Dark Grid",       sub: "Kyma-newhelp",    component: Design1 },
  { name: "SonicVault — Circle Viz",    sub: "Kymaaah",         component: Design2 },
  { name: "Keyring — TX/RX Multi-View", sub: "Kyma_again",      component: Design3 },
  { name: "Home + Full Settings",        sub: "Kymaaaaaa",       component: Design4 },
  { name: "Static — Triangle Icons",     sub: "Kymaaaaa",        component: Design5 },
  { name: "Hardware — LCD Casing",       sub: "gemui2-transmit", component: Design6 },
  { name: "Protocol Dark — Sharp",       sub: "Kyamaaam",        component: Design7 },
  { name: "Protocol Dark — Rounded",     sub: "Kymaagainnn",     component: Design8 },
  { name: "Advanced Protocol v2.0",      sub: "kyma-ui.jsx",     component: Design9 },
];

export default function DesignBrowser() {
  const [idx, setIdx] = useState(0);
  const { name, sub, component: Component } = DESIGNS[idx];

  return (
    <div style={{ minHeight: "100vh", background: "#0A0A0A" }}>
      {/* Nav Bar */}
      <div style={{
        position: "fixed", top: 0, left: 0, right: 0, zIndex: 99999,
        background: "#111118", borderBottom: "1px solid #222230",
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "0 14px", height: 48, gap: 12,
      }}>
        <button onClick={() => setIdx(i => Math.max(0, i - 1))} disabled={idx === 0}
          style={{ background: "none", border: "1px solid #2A2A40", color: idx === 0 ? "#333" : "#AAA",
            padding: "5px 14px", cursor: idx === 0 ? "not-allowed" : "pointer",
            fontFamily: "monospace", fontSize: 11, letterSpacing: "0.1em", flexShrink: 0 }}>
          ← PREV
        </button>

        <div style={{ textAlign: "center", minWidth: 0, flex: 1 }}>
          <div style={{ color: "#FFFFFF", fontFamily: "monospace", fontSize: 11, letterSpacing: "0.15em", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{name}</div>
          <div style={{ color: "#444", fontFamily: "monospace", fontSize: 9, letterSpacing: "0.1em", marginTop: 2 }}>
            {sub} &nbsp;·&nbsp; {idx + 1} / {DESIGNS.length}
          </div>
        </div>

        {/* Dot indicators */}
        <div style={{ display: "flex", gap: 5, flexShrink: 0 }}>
          {DESIGNS.map((_, i) => (
            <button key={i} onClick={() => setIdx(i)}
              style={{ width: i === idx ? 18 : 6, height: 6, borderRadius: 3,
                background: i === idx ? "#7C7CFF" : "#333",
                border: "none", cursor: "pointer", padding: 0,
                transition: "all 0.2s" }}/>
          ))}
        </div>

        <button onClick={() => setIdx(i => Math.min(DESIGNS.length - 1, i + 1))} disabled={idx === DESIGNS.length - 1}
          style={{ background: "none", border: "1px solid #2A2A40", color: idx === DESIGNS.length - 1 ? "#333" : "#AAA",
            padding: "5px 14px", cursor: idx === DESIGNS.length - 1 ? "not-allowed" : "pointer",
            fontFamily: "monospace", fontSize: 11, letterSpacing: "0.1em", flexShrink: 0 }}>
          NEXT →
        </button>
      </div>

      {/* Design Canvas */}
      <div style={{ paddingTop: 48 }}>
        <Component key={idx} />
      </div>
    </div>
  );
}
