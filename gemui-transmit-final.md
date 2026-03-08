import React, { useState, useEffect } from 'react';
import { Send, ArrowDownToLine, Check, Mic, Volume2, X, Activity } from 'lucide-react';

// --- DIETER RAMS DESIGN PRINCIPLES APPLIED ---
// 1. Unobtrusive: Minimal lines, no bounding boxes around inputs.
// 2. Understandable: Clear, human-readable labels. Hide IV/AAD/Burst technical debt.
// 3. Aesthetic: High contrast, purposeful typography, single accent color.
// 4. Less but better: Strip away everything that isn't required for the transaction.

const TX_TYPES = [
  { id: 'PAY', label: 'SOL Pay', unit: '◎', icon: '◎' },
  { id: 'SKR', label: 'SKR Tip', unit: 'SKR', icon: '⬡' },
  { id: 'SIGN', label: 'Cold Sign', unit: 'SIG', icon: '□' },
  { id: 'CNFT', label: 'cNFT Drop', unit: 'NFT', icon: '◈' },
];

export default function KymaUI() {
  const [mode, setMode] = useState('TX'); // 'TX' | 'RX'
  const [txType, setTxType] = useState(TX_TYPES[0]);
  const [amount, setAmount] = useState('');
  const [recipient, setRecipient] = useState('');
  const [memo, setMemo] = useState('');
  const [status, setStatus] = useState('IDLE'); // 'IDLE' | 'ACTIVE' | 'SUCCESS'

  // Simulate transmission/reception
  const triggerAction = () => {
    setStatus('ACTIVE');
    setTimeout(() => {
      setStatus('SUCCESS');
      setTimeout(() => setStatus('IDLE'), 3000);
    }, 2500);
  };

  const isIdle = status === 'IDLE';
  const isActive = status === 'ACTIVE';
  const isSuccess = status === 'SUCCESS';

  return (
    <div className="min-h-screen bg-[#050505] text-[#EDEDED] font-sans flex justify-center sm:p-6">
      <div className="w-full max-w-md bg-[#0A0A0A] sm:rounded-none sm:border border-white/10 overflow-hidden flex flex-col relative shadow-2xl">
        
        {/* CSS for Acoustic Visualizer & Scrollbar Hiding */}
        <style dangerouslySetInnerHTML={{__html: `
          ::-webkit-scrollbar { display: none; }
          * { -ms-overflow-style: none; scrollbar-width: none; }
          
          @keyframes wave {
            0%, 100% { transform: scaleY(0.2); }
            50% { transform: scaleY(1); }
          }
          .bar-1 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.0s; }
          .bar-2 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.1s; }
          .bar-3 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.2s; }
          .bar-4 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.3s; }
          .bar-5 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.2s; }
          .bar-6 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.1s; }
          .bar-7 { animation: wave 1.2s ease-in-out infinite; animation-delay: 0.0s; }
        `}} />

        {/* HARDWARE TOGGLE (TX / RX) - SQUARE, TOP, LARGE */}
        <div className="flex w-full border-b border-white/10 z-20">
          <button 
            onClick={() => { setMode('TX'); setStatus('IDLE'); }}
            className={`flex-1 py-6 text-sm font-bold tracking-[0.2em] uppercase transition-colors ${mode === 'TX' ? 'bg-white text-black' : 'bg-[#0A0A0A] text-white/40 hover:text-white'}`}
          >
            Transmit
          </button>
          <button 
            onClick={() => { setMode('RX'); setStatus('IDLE'); }}
            className={`flex-1 py-6 text-sm font-bold tracking-[0.2em] uppercase transition-colors ${mode === 'RX' ? 'bg-white text-black' : 'bg-[#0A0A0A] text-white/40 hover:text-white'}`}
          >
            Receive
          </button>
        </div>

        {/* MAIN CONTENT AREA */}
        <div className="flex-1 flex flex-col relative z-10">
          
          {mode === 'TX' ? (
            <div className="flex-1 flex flex-col animation-fade-in mt-4">
              
              {/* HERO DISPLAY: AMOUNT (Calculator Style - Top Centered & Fixed Height) */}
              <div className="flex flex-col items-center justify-center h-[160px]">
                {['SKR', 'PAY'].includes(txType.id) ? (
                  <>
                    <input 
                      type="number" 
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      className="bg-transparent text-7xl font-light outline-none w-full text-center placeholder:text-white/10"
                      disabled={!isIdle}
                    />
                    <span className="text-[10px] text-white/40 font-bold tracking-[0.2em] uppercase mt-4 flex items-center gap-2">
                      <span className="font-mono text-sm opacity-50">{txType.icon}</span> Total {txType.unit}
                    </span>
                  </>
                ) : (
                  <>
                    <span className="text-7xl font-light text-white/10 font-mono tracking-widest">
                      ---
                    </span>
                    <span className="text-[10px] text-white/40 font-bold tracking-[0.2em] uppercase mt-4 flex items-center gap-2">
                      {txType.id === 'SIGN' ? 'Awaiting TX Payload' : 'Event ID Required'}
                    </span>
                  </>
                )}
              </div>

              {/* TRANSACTION TYPE SELECTOR - LARGE 2x2 GRID */}
              <div className="grid grid-cols-2 gap-px bg-white/10 border-y border-white/10">
                {TX_TYPES.map(type => (
                  <button
                    key={type.id}
                    onClick={() => setTxType(type)}
                    className={`flex flex-col items-center justify-center gap-2 py-6 rounded-none transition-colors ${
                      txType.id === type.id 
                      ? 'bg-[#1A1A1A] text-white' 
                      : 'bg-[#050505] text-white/30 hover:bg-[#0A0A0A] hover:text-white/70'
                    }`}
                  >
                    <span className="font-mono text-xl opacity-80">{type.icon}</span>
                    <span className="text-[10px] font-bold tracking-[0.2em] uppercase">{type.label}</span>
                  </button>
                ))}
              </div>

              {/* DYNAMIC FORM AREA (Inputs) */}
              <div className="flex flex-col w-full gap-0 px-6 mt-6">
                
                {/* EVENT ID FIELD (For cNFT) */}
                {txType.id === 'CNFT' && (
                  <div className="w-full relative group border-b border-white/10">
                    <input 
                      type="text" 
                      placeholder="EVENT DROP ID (e.g. MONOLITH_2026)"
                      className="w-full bg-transparent py-5 text-xs font-mono placeholder:text-white/20 focus:text-white outline-none transition-colors uppercase tracking-widest"
                      disabled={!isIdle}
                    />
                  </div>
                )}

                {/* RECIPIENT / PAYLOAD FIELD */}
                {['SKR', 'PAY', 'SIGN'].includes(txType.id) && (
                  <div className="w-full relative group border-b border-white/10">
                    <input 
                      type="text" 
                      value={recipient}
                      onChange={(e) => setRecipient(e.target.value)}
                      placeholder={txType.id === 'SIGN' ? "UNSIGNED TX PAYLOAD" : "RECIPIENT ADDRESS"}
                      className="w-full bg-transparent py-5 text-xs font-mono placeholder:text-white/20 focus:text-white outline-none transition-colors pr-10 uppercase tracking-widest"
                      disabled={!isIdle}
                    />
                    {recipient && isIdle && (
                      <button 
                        onClick={() => setRecipient('')}
                        className="absolute right-0 top-1/2 -translate-y-1/2 text-white/20 hover:text-white transition-colors p-2"
                      >
                        <X size={14} />
                      </button>
                    )}
                  </div>
                )}

                {/* MEMO FIELD */}
                {['SKR', 'PAY'].includes(txType.id) && (
                  <div className="w-full relative group border-b border-white/10">
                    <input 
                      type="text" 
                      value={memo}
                      onChange={(e) => setMemo(e.target.value)}
                      placeholder="MEMO (OPTIONAL)"
                      className="w-full bg-transparent py-5 text-xs font-mono placeholder:text-white/20 focus:text-white outline-none transition-colors uppercase tracking-widest"
                      disabled={!isIdle}
                    />
                  </div>
                )}
              </div>
            </div>
          ) : (
            /* RECEIVE MODE VIEW - HARDWARE SCANNER AESTHETIC */
            <div className="flex-1 flex flex-col items-center justify-center animation-fade-in relative min-h-[400px]">
              
              {/* Top Meta Data */}
              <div className="absolute top-6 left-6 right-6 flex justify-between text-[10px] font-mono font-bold tracking-[0.2em] text-white/40 uppercase">
                <span>Band: 15-19.5 kHz</span>
                <span>Mod: mFSK</span>
              </div>

              <div className="text-center z-10 flex flex-col items-center w-full px-12">
                {/* Hardware Status Indicator */}
                <div className={`w-16 h-16 border-2 flex items-center justify-center mb-8 transition-colors duration-500 ${
                  isActive ? 'border-[#14F195] text-[#14F195]' : 
                  isSuccess ? 'border-white text-white' : 
                  'border-white/10 text-white/20'
                }`}>
                  {isSuccess ? <Check size={24} /> : <Mic size={24} className={isActive ? "animate-pulse" : ""} />}
                </div>

                <h2 className="text-xl font-bold tracking-[0.2em] uppercase mb-8">
                  {isSuccess ? 'Payload Decoded' : isActive ? 'Carrier Locked' : 'Scanning'}
                </h2>
                
                {/* Functional Readout Panel */}
                <div className="w-full bg-[#1A1A1A] p-4 border border-white/10 text-left font-mono text-[10px] text-white/60 uppercase tracking-wider space-y-3">
                  <div className="flex justify-between items-center border-b border-white/5 pb-2">
                    <span>Status</span> 
                    <span className={`font-bold ${isActive ? 'text-[#14F195]' : isSuccess ? 'text-white' : ''}`}>
                      {isSuccess ? 'COMPLETE' : isActive ? 'RECEIVING...' : 'AWAITING SIGNAL'}
                    </span>
                  </div>
                  <div className="flex justify-between items-center border-b border-white/5 pb-2">
                    <span>Protocol</span> 
                    <span>ggwave / RS-ECC</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span>Buffer</span> 
                    <span>{isSuccess ? '100%' : isActive ? '42%' : '0%'}</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ACOUSTIC VISUALIZER (Unobtrusive & Honest) */}
          <div className="h-24 w-full flex items-center justify-center gap-1.5 mt-auto mb-6 px-6">
            {[1, 2, 3, 4, 5, 6, 7].map((bar) => (
              <div 
                key={bar} 
                className={`w-1.5 rounded-none transition-all duration-300 ${
                  isActive ? `bg-[#14F195] bar-${bar}` : 
                  isSuccess ? 'bg-white h-full scale-y-100' : 'bg-white/10 h-1'
                }`}
                style={isActive ? { height: `${[40, 60, 80, 100, 80, 60, 40][bar-1]}%` } : {}}
              />
            ))}
          </div>

        </div>

        {/* BOTTOM ACTION BUTTON */}
        <div className="p-6 pt-0 z-10">
          <button
            onClick={triggerAction}
            disabled={!isIdle || (mode === 'TX' && !amount && txType.id !== 'CNFT' && txType.id !== 'SIGN')}
            className={`w-full py-5 rounded-none flex items-center justify-center gap-3 text-sm font-bold tracking-[0.2em] uppercase transition-all duration-300 relative overflow-hidden group
              ${isSuccess 
                ? 'bg-white text-black' 
                : isActive 
                  ? 'bg-[#14F195]/10 text-[#14F195] border border-[#14F195]/30' 
                  : 'bg-white text-black disabled:bg-white/5 disabled:text-white/20'
              }
            `}
          >
            {/* Subtle hover effect for Idle state */}
            {isIdle && (
               <div className="absolute inset-0 bg-black/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300" />
            )}
            
            <span className="relative z-10 flex items-center gap-2">
              {isSuccess ? (
                <><Check size={18} /> {mode === 'TX' ? 'Sent' : 'Decoded'}</>
              ) : isActive ? (
                <>{mode === 'TX' ? <Volume2 size={18} className="animate-pulse" /> : <Activity size={18} className="animate-pulse" />} {mode === 'TX' ? 'Transmitting' : 'Listening'}</>
              ) : (
                <>{mode === 'TX' ? <Send size={18} /> : <ArrowDownToLine size={18} />} {mode === 'TX' ? 'Initiate' : 'Start Listener'}</>
              )}
            </span>
          </button>
        </div>

      </div>
    </div>
  );
}