
bazel-bin/tests/verilator_sim/matrix8x8k4_tile_correctness_demo.elf:     file format elf32-littleriscv


Disassembly of section .text:

00000000 <_start>:
   0:	b0205073          	csrwi	minstret,0
   4:	b8205073          	csrwi	minstreth,0
   8:	b0202573          	csrr	a0,minstret
   c:	b82025f3          	csrr	a1,minstreth
  10:	00018117          	auipc	sp,0x18
  14:	ff010113          	addi	sp,sp,-16 # 18000 <__stack_end__>
  18:	00010197          	auipc	gp,0x10
  1c:	7f818193          	addi	gp,gp,2040 # 10810 <__global_pointer$>
  20:	00000213          	li	tp,0
  24:	00000313          	li	t1,0
  28:	00000393          	li	t2,0
  2c:	00000413          	li	s0,0
  30:	00000493          	li	s1,0
  34:	00000593          	li	a1,0
  38:	00000613          	li	a2,0
  3c:	00000693          	li	a3,0
  40:	00000713          	li	a4,0
  44:	00000793          	li	a5,0
  48:	00000813          	li	a6,0
  4c:	00000893          	li	a7,0
  50:	00000913          	li	s2,0
  54:	00000993          	li	s3,0
  58:	00000a13          	li	s4,0
  5c:	00000a93          	li	s5,0
  60:	00000b13          	li	s6,0
  64:	00000b93          	li	s7,0
  68:	00000c13          	li	s8,0
  6c:	00000c93          	li	s9,0
  70:	00000d13          	li	s10,0
  74:	00000d93          	li	s11,0
  78:	00000e13          	li	t3,0
  7c:	00000e93          	li	t4,0
  80:	00000f13          	li	t5,0
  84:	00000f93          	li	t6,0
  88:	81018513          	addi	a0,gp,-2032 # 10020 <_ZN10__cxxabiv1L12atexit_countE>
  8c:	00010597          	auipc	a1,0x10
  90:	fd858593          	addi	a1,a1,-40 # 10064 <__bss_end>
  94:	1dc000ef          	jal	270 <crt_section_clear>
  98:	2e000413          	li	s0,736
  9c:	2e000493          	li	s1,736
  a0:	00947a63          	bgeu	s0,s1,b4 <init_array_loop_end>

000000a4 <init_array_loop>:
  a4:	00042283          	lw	t0,0(s0)
  a8:	000280e7          	jalr	t0
  ac:	00440413          	addi	s0,s0,4
  b0:	fe946ae3          	bltu	s0,s1,a4 <init_array_loop>

000000b4 <init_array_loop_end>:
  b4:	00000297          	auipc	t0,0x0
  b8:	0d028293          	addi	t0,t0,208 # 184 <coralnpu_exception_handler>
  bc:	30529073          	csrw	mtvec,t0
  c0:	000062b7          	lui	t0,0x6
  c4:	60028293          	addi	t0,t0,1536 # 6600 <__fini_array_end+0x6320>
  c8:	3002a073          	csrs	mstatus,t0
  cc:	00010297          	auipc	t0,0x10
  d0:	f4428293          	addi	t0,t0,-188 # 10010 <__data_start>
  d4:	0badd537          	lui	a0,0xbadd
  d8:	00d50513          	addi	a0,a0,13 # badd00d <__stack_end__+0xbac500d>
  dc:	00a2a023          	sw	a0,0(t0)
  e0:	00000513          	li	a0,0
  e4:	00000593          	li	a1,0
  e8:	00000097          	auipc	ra,0x0
  ec:	05c08093          	addi	ra,ra,92 # 144 <main>
  f0:	000080e7          	jalr	ra
  f4:	00050913          	mv	s2,a0
  f8:	0f0000ef          	jal	1e8 <__cxa_finalize>
  fc:	2e000413          	li	s0,736
 100:	2e000493          	li	s1,736
 104:	00940a63          	beq	s0,s1,118 <fini_array_loop_end>

00000108 <fini_array_loop>:
 108:	ffc48493          	addi	s1,s1,-4
 10c:	0004a283          	lw	t0,0(s1)
 110:	000280e7          	jalr	t0
 114:	fe941ae3          	bne	s0,s1,108 <fini_array_loop>

00000118 <fini_array_loop_end>:
 118:	00090513          	mv	a0,s2
 11c:	00010297          	auipc	t0,0x10
 120:	ef428293          	addi	t0,t0,-268 # 10010 <__data_start>
 124:	00a2a023          	sw	a0,0(t0)
 128:	00050663          	beqz	a0,134 <success>

0000012c <failure>:
 12c:	00100073          	ebreak
 130:	0100006f          	j	140 <loop>

00000134 <success>:
 134:	b0202573          	csrr	a0,minstret
 138:	b82025f3          	csrr	a1,minstreth
 13c:	08000073          	.insn	4, 0x08000073

00000140 <loop>:
 140:	0000006f          	j	140 <loop>

00000144 <main>:
 144:	0000100f          	.word	0x0000100f
 148:	00100293          	li	t0,1
 14c:	7c029073          	csrw	0x7c0,t0
 150:	00014537          	lui	a0,0x14
 154:	000155b7          	lui	a1,0x15
 158:	00016637          	lui	a2,0x16
 15c:	0006005b          	.word	0x0006005b
 160:	00b5105b          	.word	0x00b5105b
 164:	0000100f          	.word	0x0000100f
 168:	00000293          	li	t0,0
 16c:	7c029073          	csrw	0x7c0,t0
 170:	00100793          	li	a5,1
 174:	00010717          	auipc	a4,0x10
 178:	e8f72623          	sw	a5,-372(a4) # 10000 <tohost>
 17c:	00000513          	li	a0,0
 180:	00008067          	ret

00000184 <coralnpu_exception_handler>:
 184:	00100073          	ebreak
 188:	0000006f          	j	188 <coralnpu_exception_handler+0x4>

0000018c <__cxa_guard_acquire>:
 18c:	00054503          	lbu	a0,0(a0) # 14000 <__global_pointer$+0x37f0>
 190:	00153513          	seqz	a0,a0
 194:	00008067          	ret

00000198 <__cxa_guard_release>:
 198:	00100793          	li	a5,1
 19c:	00f50023          	sb	a5,0(a0)
 1a0:	00008067          	ret

000001a4 <__cxa_guard_abort>:
 1a4:	00008067          	ret

000001a8 <__cxa_atexit>:
 1a8:	00010797          	auipc	a5,0x10
 1ac:	e787a783          	lw	a5,-392(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 1b0:	00700713          	li	a4,7
 1b4:	02f74663          	blt	a4,a5,1e0 <__cxa_atexit+0x38>
 1b8:	00379693          	slli	a3,a5,0x3
 1bc:	81418713          	addi	a4,gp,-2028 # 10024 <_ZN10__cxxabiv1L14atexit_entriesE>
 1c0:	00d70733          	add	a4,a4,a3
 1c4:	00a72023          	sw	a0,0(a4)
 1c8:	00b72223          	sw	a1,4(a4)
 1cc:	00178793          	addi	a5,a5,1
 1d0:	00010717          	auipc	a4,0x10
 1d4:	e4f72823          	sw	a5,-432(a4) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 1d8:	00000513          	li	a0,0
 1dc:	00008067          	ret
 1e0:	fff00513          	li	a0,-1
 1e4:	00008067          	ret

000001e8 <__cxa_finalize>:
 1e8:	ff010113          	addi	sp,sp,-16
 1ec:	00112623          	sw	ra,12(sp)
 1f0:	00912223          	sw	s1,4(sp)
 1f4:	00010797          	auipc	a5,0x10
 1f8:	e2c7a783          	lw	a5,-468(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 1fc:	fff78493          	addi	s1,a5,-1
 200:	0404c463          	bltz	s1,248 <__cxa_finalize+0x60>
 204:	00812423          	sw	s0,8(sp)
 208:	01212023          	sw	s2,0(sp)
 20c:	00379793          	slli	a5,a5,0x3
 210:	81418413          	addi	s0,gp,-2028 # 10024 <_ZN10__cxxabiv1L14atexit_entriesE>
 214:	00f40433          	add	s0,s0,a5
 218:	fff00913          	li	s2,-1
 21c:	0100006f          	j	22c <__cxa_finalize+0x44>
 220:	fff48493          	addi	s1,s1,-1
 224:	ff840413          	addi	s0,s0,-8
 228:	01248c63          	beq	s1,s2,240 <__cxa_finalize+0x58>
 22c:	ff842783          	lw	a5,-8(s0)
 230:	fe0788e3          	beqz	a5,220 <__cxa_finalize+0x38>
 234:	ffc42503          	lw	a0,-4(s0)
 238:	000780e7          	jalr	a5
 23c:	fe5ff06f          	j	220 <__cxa_finalize+0x38>
 240:	00812403          	lw	s0,8(sp)
 244:	00012903          	lw	s2,0(sp)
 248:	00010797          	auipc	a5,0x10
 24c:	dc07ac23          	sw	zero,-552(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 250:	00c12083          	lw	ra,12(sp)
 254:	00412483          	lw	s1,4(sp)
 258:	01010113          	addi	sp,sp,16
 25c:	00008067          	ret

00000260 <__cxa_pure_virtual>:
 260:	00100073          	ebreak
 264:	00008067          	ret
	...

Disassembly of section .crt:

00000270 <crt_section_clear>:
 270:	02b57063          	bgeu	a0,a1,290 <crt_section_clear+0x20>
 274:	00b562b3          	or	t0,a0,a1
 278:	0032f293          	andi	t0,t0,3
 27c:	00029e63          	bnez	t0,298 <crt_section_clear+0x28>
 280:	00052023          	sw	zero,0(a0)
 284:	00450513          	addi	a0,a0,4
 288:	feb56ce3          	bltu	a0,a1,280 <crt_section_clear+0x10>
 28c:	00008067          	ret
 290:	00b51463          	bne	a0,a1,298 <crt_section_clear+0x28>
 294:	00008067          	ret
 298:	00100073          	ebreak

0000029c <crt_section_copy>:
 29c:	02b57c63          	bgeu	a0,a1,2d4 <crt_section_copy+0x38>
 2a0:	00b562b3          	or	t0,a0,a1
 2a4:	00c2e2b3          	or	t0,t0,a2
 2a8:	0032f293          	andi	t0,t0,3
 2ac:	02029863          	bnez	t0,2dc <crt_section_copy+0x40>
 2b0:	40c502b3          	sub	t0,a0,a2
 2b4:	40a58333          	sub	t1,a1,a0
 2b8:	0262e263          	bltu	t0,t1,2dc <crt_section_copy+0x40>
 2bc:	00062283          	lw	t0,0(a2) # 16000 <__global_pointer$+0x57f0>
 2c0:	00460613          	addi	a2,a2,4
 2c4:	00552023          	sw	t0,0(a0)
 2c8:	00450513          	addi	a0,a0,4
 2cc:	feb568e3          	bltu	a0,a1,2bc <crt_section_copy+0x20>
 2d0:	00008067          	ret
 2d4:	00b51463          	bne	a0,a1,2dc <crt_section_copy+0x40>
 2d8:	00008067          	ret
 2dc:	00100073          	ebreak
