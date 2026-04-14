
bazel-out/k8-fastbuild-ST-dd8dc713f32d/bin/tests/cocotb/rvv/ml_ops/rvv_matmul_8x4_4x8.elf:     file format elf32-littleriscv


Disassembly of section .text:

00000000 <_start>:
   0:	b0205073          	csrwi	minstret,0
   4:	b8205073          	csrwi	minstreth,0
   8:	b0202573          	csrr	a0,minstret
   c:	b82025f3          	csrr	a1,minstreth
  10:	00018117          	auipc	sp,0x18
  14:	ff010113          	addi	sp,sp,-16 # 18000 <__stack_end__>
  18:	00010197          	auipc	gp,0x10
  1c:	7e818193          	addi	gp,gp,2024 # 10800 <__global_pointer$>
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
  88:	96018513          	addi	a0,gp,-1696 # 10160 <mcontext0_write_value>
  8c:	00010597          	auipc	a1,0x10
  90:	11c58593          	addi	a1,a1,284 # 101a8 <__bss_end>
  94:	32c000ef          	jal	3c0 <crt_section_clear>
  98:	43000413          	li	s0,1072
  9c:	43000493          	li	s1,1072
  a0:	00947a63          	bgeu	s0,s1,b4 <init_array_loop_end>

000000a4 <init_array_loop>:
  a4:	00042283          	lw	t0,0(s0)
  a8:	000280e7          	jalr	t0
  ac:	00440413          	addi	s0,s0,4
  b0:	fe946ae3          	bltu	s0,s1,a4 <init_array_loop>

000000b4 <init_array_loop_end>:
  b4:	00000297          	auipc	t0,0x0
  b8:	22c28293          	addi	t0,t0,556 # 2e0 <coralnpu_exception_handler>
  bc:	30529073          	csrw	mtvec,t0
  c0:	000062b7          	lui	t0,0x6
  c4:	60028293          	addi	t0,t0,1536 # 6600 <__fini_array_end+0x61d0>
  c8:	3002a073          	csrs	mstatus,t0
  cc:	00010297          	auipc	t0,0x10
  d0:	08428293          	addi	t0,t0,132 # 10150 <_ret>
  d4:	0badd537          	lui	a0,0xbadd
  d8:	00d50513          	addi	a0,a0,13 # badd00d <__stack_end__+0xbac500d>
  dc:	00a2a023          	sw	a0,0(t0)
  e0:	00000513          	li	a0,0
  e4:	00000593          	li	a1,0
  e8:	00000097          	auipc	ra,0x0
  ec:	12c08093          	addi	ra,ra,300 # 214 <main>
  f0:	000080e7          	jalr	ra
  f4:	00050913          	mv	s2,a0
  f8:	244000ef          	jal	33c <__cxa_finalize>
  fc:	43000413          	li	s0,1072
 100:	43000493          	li	s1,1072
 104:	00940a63          	beq	s0,s1,118 <fini_array_loop_end>

00000108 <fini_array_loop>:
 108:	ffc48493          	addi	s1,s1,-4
 10c:	0004a283          	lw	t0,0(s1)
 110:	000280e7          	jalr	t0
 114:	fe941ae3          	bne	s0,s1,108 <fini_array_loop>

00000118 <fini_array_loop_end>:
 118:	00090513          	mv	a0,s2
 11c:	00010297          	auipc	t0,0x10
 120:	03428293          	addi	t0,t0,52 # 10150 <_ret>
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

00000144 <_Z6MatMuljjjPKaS0_Pl>:
 144:	0c050663          	beqz	a0,210 <_Z6MatMuljjjPKaS0_Pl+0xcc>
 148:	ff010113          	addi	sp,sp,-16
 14c:	00812623          	sw	s0,12(sp)
 150:	00050293          	mv	t0,a0
 154:	00060f93          	mv	t6,a2
 158:	00068893          	mv	a7,a3
 15c:	00070313          	mv	t1,a4
 160:	c2202573          	csrr	a0,vlenb
 164:	00261413          	slli	s0,a2,0x2
 168:	00878eb3          	add	t4,a5,s0
 16c:	40c003b3          	neg	t2,a2
 170:	00239393          	slli	t2,t2,0x2
 174:	00000813          	li	a6,0
 178:	00000f13          	li	t5,0
 17c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 180:	42006357          	vmv.s.x	v6,zero
 184:	060f8463          	beqz	t6,1ec <_Z6MatMuljjjPKaS0_Pl+0xa8>
 188:	007e8e33          	add	t3,t4,t2
 18c:	00000613          	li	a2,0
 190:	06058c63          	beqz	a1,208 <_Z6MatMuljjjPKaS0_Pl+0xc4>
 194:	00050713          	mv	a4,a0
 198:	9e6030d7          	vmv1r.v	v1,v6
 19c:	00000793          	li	a5,0
 1a0:	40f586b3          	sub	a3,a1,a5
 1a4:	0ad75733          	minu	a4,a4,a3
 1a8:	010786b3          	add	a3,a5,a6
 1ac:	00d886b3          	add	a3,a7,a3
 1b0:	0c077057          	vsetvli	zero,a4,e8,m1,ta,ma
 1b4:	02068287          	vle8.v	v5,(a3)
 1b8:	00f606b3          	add	a3,a2,a5
 1bc:	00d306b3          	add	a3,t1,a3
 1c0:	02068207          	vle8.v	v4,(a3)
 1c4:	ee522157          	vwmul.vv	v2,v5,v4
 1c8:	0c957057          	vsetvli	zero,a0,e16,m2,ta,ma
 1cc:	c62080d7          	vwredsum.vs	v1,v2,v1
 1d0:	00e787b3          	add	a5,a5,a4
 1d4:	fcb7e6e3          	bltu	a5,a1,1a0 <_Z6MatMuljjjPKaS0_Pl+0x5c>
 1d8:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 1dc:	020e60a7          	vse32.v	v1,(t3)
 1e0:	004e0e13          	addi	t3,t3,4
 1e4:	00b60633          	add	a2,a2,a1
 1e8:	fbde14e3          	bne	t3,t4,190 <_Z6MatMuljjjPKaS0_Pl+0x4c>
 1ec:	001f0f13          	addi	t5,t5,1
 1f0:	008e8eb3          	add	t4,t4,s0
 1f4:	00b80833          	add	a6,a6,a1
 1f8:	f9e296e3          	bne	t0,t5,184 <_Z6MatMuljjjPKaS0_Pl+0x40>
 1fc:	00c12403          	lw	s0,12(sp)
 200:	01010113          	addi	sp,sp,16
 204:	00008067          	ret
 208:	9e6030d7          	vmv1r.v	v1,v6
 20c:	fd1ff06f          	j	1dc <_Z6MatMuljjjPKaS0_Pl+0x98>
 210:	00008067          	ret

00000214 <main>:
 214:	fe010113          	addi	sp,sp,-32
 218:	00112e23          	sw	ra,28(sp)
 21c:	00812c23          	sw	s0,24(sp)
 220:	00912a23          	sw	s1,20(sp)
 224:	01212823          	sw	s2,16(sp)
 228:	01312623          	sw	s3,12(sp)
 22c:	00010417          	auipc	s0,0x10
 230:	dd440413          	addi	s0,s0,-556 # 10000 <bench_result>
 234:	00100793          	li	a5,1
 238:	00f42023          	sw	a5,0(s0)
 23c:	00042223          	sw	zero,4(s0)
 240:	00042423          	sw	zero,8(s0)
 244:	00042623          	sw	zero,12(s0)
 248:	96018913          	addi	s2,gp,-1696 # 10160 <mcontext0_write_value>
 24c:	00f92023          	sw	a5,0(s2)
 250:	7c079073          	csrw	0x7c0,a5
 254:	b80027f3          	csrr	a5,mcycleh
 258:	b00024f3          	csrr	s1,mcycle
 25c:	b80029f3          	csrr	s3,mcycleh
 260:	ff379ae3          	bne	a5,s3,254 <main+0x40>
 264:	81018793          	addi	a5,gp,-2032 # 10010 <result_output>
 268:	91018713          	addi	a4,gp,-1776 # 10110 <rhs_input>
 26c:	93018693          	addi	a3,gp,-1744 # 10130 <lhs_input>
 270:	00800613          	li	a2,8
 274:	00400593          	li	a1,4
 278:	00060513          	mv	a0,a2
 27c:	ec9ff0ef          	jal	144 <_Z6MatMuljjjPKaS0_Pl>
 280:	b8002773          	csrr	a4,mcycleh
 284:	b00026f3          	csrr	a3,mcycle
 288:	b80027f3          	csrr	a5,mcycleh
 28c:	fef71ae3          	bne	a4,a5,280 <main+0x6c>
 290:	00092023          	sw	zero,0(s2)
 294:	00000713          	li	a4,0
 298:	7c071073          	csrw	0x7c0,a4
 29c:	40968733          	sub	a4,a3,s1
 2a0:	00e6b6b3          	sltu	a3,a3,a4
 2a4:	413787b3          	sub	a5,a5,s3
 2a8:	40d787b3          	sub	a5,a5,a3
 2ac:	00e42223          	sw	a4,4(s0)
 2b0:	00f42423          	sw	a5,8(s0)
 2b4:	4d4c57b7          	lui	a5,0x4d4c5
 2b8:	f5078793          	addi	a5,a5,-176 # 4d4c4f50 <__extbss_end__+0x2d4c4f50>
 2bc:	00f42623          	sw	a5,12(s0)
 2c0:	00000513          	li	a0,0
 2c4:	01c12083          	lw	ra,28(sp)
 2c8:	01812403          	lw	s0,24(sp)
 2cc:	01412483          	lw	s1,20(sp)
 2d0:	01012903          	lw	s2,16(sp)
 2d4:	00c12983          	lw	s3,12(sp)
 2d8:	02010113          	addi	sp,sp,32
 2dc:	00008067          	ret

000002e0 <coralnpu_exception_handler>:
 2e0:	00100073          	ebreak
 2e4:	0000006f          	j	2e4 <coralnpu_exception_handler+0x4>

000002e8 <__cxa_guard_acquire>:
 2e8:	00054503          	lbu	a0,0(a0)
 2ec:	00153513          	seqz	a0,a0
 2f0:	00008067          	ret

000002f4 <__cxa_guard_release>:
 2f4:	00100793          	li	a5,1
 2f8:	00f50023          	sb	a5,0(a0)
 2fc:	00008067          	ret

00000300 <__cxa_guard_abort>:
 300:	00008067          	ret

00000304 <__cxa_atexit>:
 304:	9641a783          	lw	a5,-1692(gp) # 10164 <_ZN10__cxxabiv1L12atexit_countE>
 308:	00700713          	li	a4,7
 30c:	02f74463          	blt	a4,a5,334 <__cxa_atexit+0x30>
 310:	00379693          	slli	a3,a5,0x3
 314:	96818713          	addi	a4,gp,-1688 # 10168 <_ZN10__cxxabiv1L14atexit_entriesE>
 318:	00d70733          	add	a4,a4,a3
 31c:	00a72023          	sw	a0,0(a4)
 320:	00b72223          	sw	a1,4(a4)
 324:	00178793          	addi	a5,a5,1
 328:	96f1a223          	sw	a5,-1692(gp) # 10164 <_ZN10__cxxabiv1L12atexit_countE>
 32c:	00000513          	li	a0,0
 330:	00008067          	ret
 334:	fff00513          	li	a0,-1
 338:	00008067          	ret

0000033c <__cxa_finalize>:
 33c:	ff010113          	addi	sp,sp,-16
 340:	00112623          	sw	ra,12(sp)
 344:	00912223          	sw	s1,4(sp)
 348:	9641a783          	lw	a5,-1692(gp) # 10164 <_ZN10__cxxabiv1L12atexit_countE>
 34c:	fff78493          	addi	s1,a5,-1
 350:	0404c463          	bltz	s1,398 <__cxa_finalize+0x5c>
 354:	00812423          	sw	s0,8(sp)
 358:	01212023          	sw	s2,0(sp)
 35c:	00379793          	slli	a5,a5,0x3
 360:	96818413          	addi	s0,gp,-1688 # 10168 <_ZN10__cxxabiv1L14atexit_entriesE>
 364:	00f40433          	add	s0,s0,a5
 368:	fff00913          	li	s2,-1
 36c:	0100006f          	j	37c <__cxa_finalize+0x40>
 370:	fff48493          	addi	s1,s1,-1
 374:	ff840413          	addi	s0,s0,-8
 378:	01248c63          	beq	s1,s2,390 <__cxa_finalize+0x54>
 37c:	ff842783          	lw	a5,-8(s0)
 380:	fe0788e3          	beqz	a5,370 <__cxa_finalize+0x34>
 384:	ffc42503          	lw	a0,-4(s0)
 388:	000780e7          	jalr	a5
 38c:	fe5ff06f          	j	370 <__cxa_finalize+0x34>
 390:	00812403          	lw	s0,8(sp)
 394:	00012903          	lw	s2,0(sp)
 398:	9601a223          	sw	zero,-1692(gp) # 10164 <_ZN10__cxxabiv1L12atexit_countE>
 39c:	00c12083          	lw	ra,12(sp)
 3a0:	00412483          	lw	s1,4(sp)
 3a4:	01010113          	addi	sp,sp,16
 3a8:	00008067          	ret

000003ac <__cxa_pure_virtual>:
 3ac:	00100073          	ebreak
 3b0:	00008067          	ret
	...

Disassembly of section .crt:

000003c0 <crt_section_clear>:
 3c0:	02b57063          	bgeu	a0,a1,3e0 <crt_section_clear+0x20>
 3c4:	00b562b3          	or	t0,a0,a1
 3c8:	0032f293          	andi	t0,t0,3
 3cc:	00029e63          	bnez	t0,3e8 <crt_section_clear+0x28>
 3d0:	00052023          	sw	zero,0(a0)
 3d4:	00450513          	addi	a0,a0,4
 3d8:	feb56ce3          	bltu	a0,a1,3d0 <crt_section_clear+0x10>
 3dc:	00008067          	ret
 3e0:	00b51463          	bne	a0,a1,3e8 <crt_section_clear+0x28>
 3e4:	00008067          	ret
 3e8:	00100073          	ebreak

000003ec <crt_section_copy>:
 3ec:	02b57c63          	bgeu	a0,a1,424 <crt_section_copy+0x38>
 3f0:	00b562b3          	or	t0,a0,a1
 3f4:	00c2e2b3          	or	t0,t0,a2
 3f8:	0032f293          	andi	t0,t0,3
 3fc:	02029863          	bnez	t0,42c <crt_section_copy+0x40>
 400:	40c502b3          	sub	t0,a0,a2
 404:	40a58333          	sub	t1,a1,a0
 408:	0262e263          	bltu	t0,t1,42c <crt_section_copy+0x40>
 40c:	00062283          	lw	t0,0(a2)
 410:	00460613          	addi	a2,a2,4
 414:	00552023          	sw	t0,0(a0)
 418:	00450513          	addi	a0,a0,4
 41c:	feb568e3          	bltu	a0,a1,40c <crt_section_copy+0x20>
 420:	00008067          	ret
 424:	00b51463          	bne	a0,a1,42c <crt_section_copy+0x40>
 428:	00008067          	ret
 42c:	00100073          	ebreak
