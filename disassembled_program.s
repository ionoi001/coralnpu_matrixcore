
bazel-bin/tests/verilator_sim/matrix_minimal_demo.elf:     file format elf32-littleriscv


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
  94:	25c000ef          	jal	2f0 <crt_section_clear>
  98:	36000413          	li	s0,864
  9c:	36000493          	li	s1,864
  a0:	00947a63          	bgeu	s0,s1,b4 <init_array_loop_end>

000000a4 <init_array_loop>:
  a4:	00042283          	lw	t0,0(s0)
  a8:	000280e7          	jalr	t0
  ac:	00440413          	addi	s0,s0,4
  b0:	fe946ae3          	bltu	s0,s1,a4 <init_array_loop>

000000b4 <init_array_loop_end>:
  b4:	00000297          	auipc	t0,0x0
  b8:	15028293          	addi	t0,t0,336 # 204 <coralnpu_exception_handler>
  bc:	30529073          	csrw	mtvec,t0
  c0:	000062b7          	lui	t0,0x6
  c4:	60028293          	addi	t0,t0,1536 # 6600 <a.1+0x6298>
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
  f8:	170000ef          	jal	268 <__cxa_finalize>
  fc:	36000413          	li	s0,864
 100:	36000493          	li	s1,864
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
 144:	000147b7          	lui	a5,0x14
 148:	fffec8b7          	lui	a7,0xfffec
 14c:	36800813          	li	a6,872
 150:	00001537          	lui	a0,0x1
 154:	36000593          	li	a1,864
 158:	00878613          	addi	a2,a5,8 # 14008 <__global_pointer$+0x37f8>
 15c:	01178733          	add	a4,a5,a7
 160:	010706b3          	add	a3,a4,a6
 164:	0006c683          	lbu	a3,0(a3)
 168:	00d78023          	sb	a3,0(a5)
 16c:	00a786b3          	add	a3,a5,a0
 170:	00b70733          	add	a4,a4,a1
 174:	00074703          	lbu	a4,0(a4)
 178:	00e68023          	sb	a4,0(a3)
 17c:	00178793          	addi	a5,a5,1
 180:	fcc79ee3          	bne	a5,a2,15c <main+0x18>
 184:	00014537          	lui	a0,0x14
 188:	000155b7          	lui	a1,0x15
 18c:	00016637          	lui	a2,0x16
 190:	0006005b          	.word	0x0006005b
 194:	00b5105b          	.word	0x00b5105b
 198:	0ff0000f          	fence
 19c:	000167b7          	lui	a5,0x16
 1a0:	0007a783          	lw	a5,0(a5) # 16000 <__global_pointer$+0x57f0>
 1a4:	00016737          	lui	a4,0x16
 1a8:	00472703          	lw	a4,4(a4) # 16004 <__global_pointer$+0x57f4>
 1ac:	000166b7          	lui	a3,0x16
 1b0:	0086a683          	lw	a3,8(a3) # 16008 <__global_pointer$+0x57f8>
 1b4:	00016637          	lui	a2,0x16
 1b8:	00c62603          	lw	a2,12(a2) # 1600c <__global_pointer$+0x57fc>
 1bc:	fce78793          	addi	a5,a5,-50
 1c0:	02079663          	bnez	a5,1ec <main+0xa8>
 1c4:	fc470713          	addi	a4,a4,-60
 1c8:	02071263          	bnez	a4,1ec <main+0xa8>
 1cc:	f8e68693          	addi	a3,a3,-114
 1d0:	00069e63          	bnez	a3,1ec <main+0xa8>
 1d4:	f7460613          	addi	a2,a2,-140
 1d8:	00061a63          	bnez	a2,1ec <main+0xa8>
 1dc:	00100793          	li	a5,1
 1e0:	00010717          	auipc	a4,0x10
 1e4:	e2f72023          	sw	a5,-480(a4) # 10000 <tohost>
 1e8:	0140006f          	j	1fc <main+0xb8>
 1ec:	000017b7          	lui	a5,0x1
 1f0:	bad78793          	addi	a5,a5,-1107 # bad <a.1+0x845>
 1f4:	00010717          	auipc	a4,0x10
 1f8:	e0f72623          	sw	a5,-500(a4) # 10000 <tohost>
 1fc:	00000513          	li	a0,0
 200:	00008067          	ret

00000204 <coralnpu_exception_handler>:
 204:	00100073          	ebreak
 208:	0000006f          	j	208 <coralnpu_exception_handler+0x4>

0000020c <__cxa_guard_acquire>:
 20c:	00054503          	lbu	a0,0(a0) # 14000 <__global_pointer$+0x37f0>
 210:	00153513          	seqz	a0,a0
 214:	00008067          	ret

00000218 <__cxa_guard_release>:
 218:	00100793          	li	a5,1
 21c:	00f50023          	sb	a5,0(a0)
 220:	00008067          	ret

00000224 <__cxa_guard_abort>:
 224:	00008067          	ret

00000228 <__cxa_atexit>:
 228:	00010797          	auipc	a5,0x10
 22c:	df87a783          	lw	a5,-520(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 230:	00700713          	li	a4,7
 234:	02f74663          	blt	a4,a5,260 <__cxa_atexit+0x38>
 238:	00379693          	slli	a3,a5,0x3
 23c:	81418713          	addi	a4,gp,-2028 # 10024 <_ZN10__cxxabiv1L14atexit_entriesE>
 240:	00d70733          	add	a4,a4,a3
 244:	00a72023          	sw	a0,0(a4)
 248:	00b72223          	sw	a1,4(a4)
 24c:	00178793          	addi	a5,a5,1
 250:	00010717          	auipc	a4,0x10
 254:	dcf72823          	sw	a5,-560(a4) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 258:	00000513          	li	a0,0
 25c:	00008067          	ret
 260:	fff00513          	li	a0,-1
 264:	00008067          	ret

00000268 <__cxa_finalize>:
 268:	ff010113          	addi	sp,sp,-16
 26c:	00112623          	sw	ra,12(sp)
 270:	00912223          	sw	s1,4(sp)
 274:	00010797          	auipc	a5,0x10
 278:	dac7a783          	lw	a5,-596(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 27c:	fff78493          	addi	s1,a5,-1
 280:	0404c463          	bltz	s1,2c8 <__cxa_finalize+0x60>
 284:	00812423          	sw	s0,8(sp)
 288:	01212023          	sw	s2,0(sp)
 28c:	00379793          	slli	a5,a5,0x3
 290:	81418413          	addi	s0,gp,-2028 # 10024 <_ZN10__cxxabiv1L14atexit_entriesE>
 294:	00f40433          	add	s0,s0,a5
 298:	fff00913          	li	s2,-1
 29c:	0100006f          	j	2ac <__cxa_finalize+0x44>
 2a0:	fff48493          	addi	s1,s1,-1
 2a4:	ff840413          	addi	s0,s0,-8
 2a8:	01248c63          	beq	s1,s2,2c0 <__cxa_finalize+0x58>
 2ac:	ff842783          	lw	a5,-8(s0)
 2b0:	fe0788e3          	beqz	a5,2a0 <__cxa_finalize+0x38>
 2b4:	ffc42503          	lw	a0,-4(s0)
 2b8:	000780e7          	jalr	a5
 2bc:	fe5ff06f          	j	2a0 <__cxa_finalize+0x38>
 2c0:	00812403          	lw	s0,8(sp)
 2c4:	00012903          	lw	s2,0(sp)
 2c8:	00010797          	auipc	a5,0x10
 2cc:	d407ac23          	sw	zero,-680(a5) # 10020 <_ZN10__cxxabiv1L12atexit_countE>
 2d0:	00c12083          	lw	ra,12(sp)
 2d4:	00412483          	lw	s1,4(sp)
 2d8:	01010113          	addi	sp,sp,16
 2dc:	00008067          	ret

000002e0 <__cxa_pure_virtual>:
 2e0:	00100073          	ebreak
 2e4:	00008067          	ret
	...

Disassembly of section .crt:

000002f0 <crt_section_clear>:
 2f0:	02b57063          	bgeu	a0,a1,310 <crt_section_clear+0x20>
 2f4:	00b562b3          	or	t0,a0,a1
 2f8:	0032f293          	andi	t0,t0,3
 2fc:	00029e63          	bnez	t0,318 <crt_section_clear+0x28>
 300:	00052023          	sw	zero,0(a0)
 304:	00450513          	addi	a0,a0,4
 308:	feb56ce3          	bltu	a0,a1,300 <crt_section_clear+0x10>
 30c:	00008067          	ret
 310:	00b51463          	bne	a0,a1,318 <crt_section_clear+0x28>
 314:	00008067          	ret
 318:	00100073          	ebreak

0000031c <crt_section_copy>:
 31c:	02b57c63          	bgeu	a0,a1,354 <crt_section_copy+0x38>
 320:	00b562b3          	or	t0,a0,a1
 324:	00c2e2b3          	or	t0,t0,a2
 328:	0032f293          	andi	t0,t0,3
 32c:	02029863          	bnez	t0,35c <crt_section_copy+0x40>
 330:	40c502b3          	sub	t0,a0,a2
 334:	40a58333          	sub	t1,a1,a0
 338:	0262e263          	bltu	t0,t1,35c <crt_section_copy+0x40>
 33c:	00062283          	lw	t0,0(a2)
 340:	00460613          	addi	a2,a2,4
 344:	00552023          	sw	t0,0(a0)
 348:	00450513          	addi	a0,a0,4
 34c:	feb568e3          	bltu	a0,a1,33c <crt_section_copy+0x20>
 350:	00008067          	ret
 354:	00b51463          	bne	a0,a1,35c <crt_section_copy+0x40>
 358:	00008067          	ret
 35c:	00100073          	ebreak
