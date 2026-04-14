
bazel-bin/tests/cocotb/rvv/ml_ops/rvv_mac_peak_vmul_bench.elf:     file format elf32-littleriscv


Disassembly of section .text:

00000000 <_start>:
   0:	b0205073          	csrwi	minstret,0
   4:	b8205073          	csrwi	minstreth,0
   8:	b0202573          	csrr	a0,minstret
   c:	b82025f3          	csrr	a1,minstreth
  10:	00108117          	auipc	sp,0x108
  14:	ff010113          	addi	sp,sp,-16 # 108000 <__stack_end__>
  18:	00100197          	auipc	gp,0x100
  1c:	7e818193          	addi	gp,gp,2024 # 100800 <__global_pointer$>
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
  88:	83018513          	addi	a0,gp,-2000 # 100030 <mcontext0_write_value>
  8c:	00100597          	auipc	a1,0x100
  90:	3f458593          	addi	a1,a1,1012 # 100480 <__bss_end>
  94:	2fc000ef          	jal	390 <crt_section_clear>
  98:	40000413          	li	s0,1024
  9c:	40000493          	li	s1,1024
  a0:	00947a63          	bgeu	s0,s1,b4 <init_array_loop_end>

000000a4 <init_array_loop>:
  a4:	00042283          	lw	t0,0(s0)
  a8:	000280e7          	jalr	t0
  ac:	00440413          	addi	s0,s0,4
  b0:	fe946ae3          	bltu	s0,s1,a4 <init_array_loop>

000000b4 <init_array_loop_end>:
  b4:	00000297          	auipc	t0,0x0
  b8:	20828293          	addi	t0,t0,520 # 2bc <coralnpu_exception_handler>
  bc:	30529073          	csrw	mtvec,t0
  c0:	000062b7          	lui	t0,0x6
  c4:	60028293          	addi	t0,t0,1536 # 6600 <__fini_array_end+0x6200>
  c8:	3002a073          	csrs	mstatus,t0
  cc:	00100297          	auipc	t0,0x100
  d0:	f5428293          	addi	t0,t0,-172 # 100020 <_ret>
  d4:	0badd537          	lui	a0,0xbadd
  d8:	00d50513          	addi	a0,a0,13 # badd00d <__stack_end__+0xb9d500d>
  dc:	00a2a023          	sw	a0,0(t0)
  e0:	00000513          	li	a0,0
  e4:	00000593          	li	a1,0
  e8:	00000097          	auipc	ra,0x0
  ec:	15c08093          	addi	ra,ra,348 # 244 <main>
  f0:	000080e7          	jalr	ra
  f4:	00050913          	mv	s2,a0
  f8:	220000ef          	jal	318 <__cxa_finalize>
  fc:	40000413          	li	s0,1024
 100:	40000493          	li	s1,1024
 104:	00940a63          	beq	s0,s1,118 <fini_array_loop_end>

00000108 <fini_array_loop>:
 108:	ffc48493          	addi	s1,s1,-4
 10c:	0004a283          	lw	t0,0(s1)
 110:	000280e7          	jalr	t0
 114:	fe941ae3          	bne	s0,s1,108 <fini_array_loop>

00000118 <fini_array_loop_end>:
 118:	00090513          	mv	a0,s2
 11c:	00100297          	auipc	t0,0x100
 120:	f0428293          	addi	t0,t0,-252 # 100020 <_ret>
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

00000144 <_ZL25measure_vmul_inner_cyclesv>:
 144:	0d0077d7          	vsetvli	a5,zero,e32,m1,ta,ma
 148:	5e01b0d7          	vmv.v.i	v1,3
 14c:	11111737          	lui	a4,0x11111
 150:	11170713          	addi	a4,a4,273 # 11111111 <__stack_end__+0x11009111>
 154:	5e074157          	vmv.v.x	v2,a4
 158:	22222737          	lui	a4,0x22222
 15c:	22270713          	addi	a4,a4,546 # 22222222 <__extbss_end__+0x2222222>
 160:	5e0744d7          	vmv.v.x	v9,a4
 164:	33333737          	lui	a4,0x33333
 168:	33370713          	addi	a4,a4,819 # 33333333 <__extbss_end__+0x13333333>
 16c:	5e074457          	vmv.v.x	v8,a4
 170:	44444737          	lui	a4,0x44444
 174:	44470713          	addi	a4,a4,1092 # 44444444 <__extbss_end__+0x24444444>
 178:	5e0743d7          	vmv.v.x	v7,a4
 17c:	55555737          	lui	a4,0x55555
 180:	55570713          	addi	a4,a4,1365 # 55555555 <__extbss_end__+0x35555555>
 184:	5e074357          	vmv.v.x	v6,a4
 188:	66666737          	lui	a4,0x66666
 18c:	66670713          	addi	a4,a4,1638 # 66666666 <__extbss_end__+0x46666666>
 190:	5e0742d7          	vmv.v.x	v5,a4
 194:	77777737          	lui	a4,0x77777
 198:	77770713          	addi	a4,a4,1911 # 77777777 <__extbss_end__+0x57777777>
 19c:	5e074257          	vmv.v.x	v4,a4
 1a0:	88889737          	lui	a4,0x88889
 1a4:	88870713          	addi	a4,a4,-1912 # 88888888 <__extbss_end__+0x68888888>
 1a8:	5e0741d7          	vmv.v.x	v3,a4
 1ac:	b8002773          	csrr	a4,mcycleh
 1b0:	b0002573          	csrr	a0,mcycle
 1b4:	b8002673          	csrr	a2,mcycleh
 1b8:	fec71ae3          	bne	a4,a2,1ac <_ZL25measure_vmul_inner_cyclesv+0x68>
 1bc:	00001737          	lui	a4,0x1
 1c0:	0d0077d7          	vsetvli	a5,zero,e32,m1,ta,ma
 1c4:	9620a157          	vmul.vv	v2,v2,v1
 1c8:	9690a4d7          	vmul.vv	v9,v9,v1
 1cc:	9680a457          	vmul.vv	v8,v8,v1
 1d0:	9670a3d7          	vmul.vv	v7,v7,v1
 1d4:	9660a357          	vmul.vv	v6,v6,v1
 1d8:	9650a2d7          	vmul.vv	v5,v5,v1
 1dc:	9640a257          	vmul.vv	v4,v4,v1
 1e0:	9630a1d7          	vmul.vv	v3,v3,v1
 1e4:	fff70713          	addi	a4,a4,-1 # fff <__fini_array_end+0xbff>
 1e8:	fc071ee3          	bnez	a4,1c4 <_ZL25measure_vmul_inner_cyclesv+0x80>
 1ec:	b80026f3          	csrr	a3,mcycleh
 1f0:	b0002773          	csrr	a4,mcycle
 1f4:	b80025f3          	csrr	a1,mcycleh
 1f8:	feb69ae3          	bne	a3,a1,1ec <_ZL25measure_vmul_inner_cyclesv+0xa8>
 1fc:	0d0077d7          	vsetvli	a5,zero,e32,m1,ta,ma
 200:	022480d7          	vadd.vv	v1,v2,v9
 204:	021400d7          	vadd.vv	v1,v1,v8
 208:	021380d7          	vadd.vv	v1,v1,v7
 20c:	021300d7          	vadd.vv	v1,v1,v6
 210:	021280d7          	vadd.vv	v1,v1,v5
 214:	021200d7          	vadd.vv	v1,v1,v4
 218:	021180d7          	vadd.vv	v1,v1,v3
 21c:	84018693          	addi	a3,gp,-1984 # 100040 <_ZL14peak_vec_spill>
 220:	0206e0a7          	vse32.v	v1,(a3)
 224:	0006a783          	lw	a5,0(a3)
 228:	00100697          	auipc	a3,0x100
 22c:	dcf6ac23          	sw	a5,-552(a3) # 100000 <peak_sink>
 230:	40a70533          	sub	a0,a4,a0
 234:	00a73733          	sltu	a4,a4,a0
 238:	40c585b3          	sub	a1,a1,a2
 23c:	40e585b3          	sub	a1,a1,a4
 240:	00008067          	ret

00000244 <main>:
 244:	ff010113          	addi	sp,sp,-16
 248:	00112623          	sw	ra,12(sp)
 24c:	00812423          	sw	s0,8(sp)
 250:	00912223          	sw	s1,4(sp)
 254:	00100417          	auipc	s0,0x100
 258:	dac40413          	addi	s0,s0,-596 # 100000 <peak_sink>
 25c:	000087b7          	lui	a5,0x8
 260:	00f42823          	sw	a5,16(s0)
 264:	00042a23          	sw	zero,20(s0)
 268:	00042c23          	sw	zero,24(s0)
 26c:	00042e23          	sw	zero,28(s0)
 270:	83018493          	addi	s1,gp,-2000 # 100030 <mcontext0_write_value>
 274:	00100793          	li	a5,1
 278:	00f4a023          	sw	a5,0(s1)
 27c:	7c079073          	csrw	0x7c0,a5
 280:	ec5ff0ef          	jal	144 <_ZL25measure_vmul_inner_cyclesv>
 284:	0004a023          	sw	zero,0(s1)
 288:	00000793          	li	a5,0
 28c:	7c079073          	csrw	0x7c0,a5
 290:	00a42a23          	sw	a0,20(s0)
 294:	00b42c23          	sw	a1,24(s0)
 298:	564d57b7          	lui	a5,0x564d5
 29c:	54c78793          	addi	a5,a5,1356 # 564d554c <__extbss_end__+0x364d554c>
 2a0:	00f42e23          	sw	a5,28(s0)
 2a4:	00000513          	li	a0,0
 2a8:	00c12083          	lw	ra,12(sp)
 2ac:	00812403          	lw	s0,8(sp)
 2b0:	00412483          	lw	s1,4(sp)
 2b4:	01010113          	addi	sp,sp,16
 2b8:	00008067          	ret

000002bc <coralnpu_exception_handler>:
 2bc:	00100073          	ebreak
 2c0:	0000006f          	j	2c0 <coralnpu_exception_handler+0x4>

000002c4 <__cxa_guard_acquire>:
 2c4:	00054503          	lbu	a0,0(a0)
 2c8:	00153513          	seqz	a0,a0
 2cc:	00008067          	ret

000002d0 <__cxa_guard_release>:
 2d0:	00100793          	li	a5,1
 2d4:	00f50023          	sb	a5,0(a0)
 2d8:	00008067          	ret

000002dc <__cxa_guard_abort>:
 2dc:	00008067          	ret

000002e0 <__cxa_atexit>:
 2e0:	8341a783          	lw	a5,-1996(gp) # 100034 <_ZN10__cxxabiv1L12atexit_countE>
 2e4:	00700713          	li	a4,7
 2e8:	02f74463          	blt	a4,a5,310 <__cxa_atexit+0x30>
 2ec:	00379693          	slli	a3,a5,0x3
 2f0:	c4018713          	addi	a4,gp,-960 # 100440 <_ZN10__cxxabiv1L14atexit_entriesE>
 2f4:	00d70733          	add	a4,a4,a3
 2f8:	00a72023          	sw	a0,0(a4)
 2fc:	00b72223          	sw	a1,4(a4)
 300:	00178793          	addi	a5,a5,1
 304:	82f1aa23          	sw	a5,-1996(gp) # 100034 <_ZN10__cxxabiv1L12atexit_countE>
 308:	00000513          	li	a0,0
 30c:	00008067          	ret
 310:	fff00513          	li	a0,-1
 314:	00008067          	ret

00000318 <__cxa_finalize>:
 318:	ff010113          	addi	sp,sp,-16
 31c:	00112623          	sw	ra,12(sp)
 320:	00912223          	sw	s1,4(sp)
 324:	8341a783          	lw	a5,-1996(gp) # 100034 <_ZN10__cxxabiv1L12atexit_countE>
 328:	fff78493          	addi	s1,a5,-1
 32c:	0404c463          	bltz	s1,374 <__cxa_finalize+0x5c>
 330:	00812423          	sw	s0,8(sp)
 334:	01212023          	sw	s2,0(sp)
 338:	00379793          	slli	a5,a5,0x3
 33c:	c4018413          	addi	s0,gp,-960 # 100440 <_ZN10__cxxabiv1L14atexit_entriesE>
 340:	00f40433          	add	s0,s0,a5
 344:	fff00913          	li	s2,-1
 348:	0100006f          	j	358 <__cxa_finalize+0x40>
 34c:	fff48493          	addi	s1,s1,-1
 350:	ff840413          	addi	s0,s0,-8
 354:	01248c63          	beq	s1,s2,36c <__cxa_finalize+0x54>
 358:	ff842783          	lw	a5,-8(s0)
 35c:	fe0788e3          	beqz	a5,34c <__cxa_finalize+0x34>
 360:	ffc42503          	lw	a0,-4(s0)
 364:	000780e7          	jalr	a5
 368:	fe5ff06f          	j	34c <__cxa_finalize+0x34>
 36c:	00812403          	lw	s0,8(sp)
 370:	00012903          	lw	s2,0(sp)
 374:	8201aa23          	sw	zero,-1996(gp) # 100034 <_ZN10__cxxabiv1L12atexit_countE>
 378:	00c12083          	lw	ra,12(sp)
 37c:	00412483          	lw	s1,4(sp)
 380:	01010113          	addi	sp,sp,16
 384:	00008067          	ret

00000388 <__cxa_pure_virtual>:
 388:	00100073          	ebreak
 38c:	00008067          	ret

Disassembly of section .crt:

00000390 <crt_section_clear>:
 390:	02b57063          	bgeu	a0,a1,3b0 <crt_section_clear+0x20>
 394:	00b562b3          	or	t0,a0,a1
 398:	0032f293          	andi	t0,t0,3
 39c:	00029e63          	bnez	t0,3b8 <crt_section_clear+0x28>
 3a0:	00052023          	sw	zero,0(a0)
 3a4:	00450513          	addi	a0,a0,4
 3a8:	feb56ce3          	bltu	a0,a1,3a0 <crt_section_clear+0x10>
 3ac:	00008067          	ret
 3b0:	00b51463          	bne	a0,a1,3b8 <crt_section_clear+0x28>
 3b4:	00008067          	ret
 3b8:	00100073          	ebreak

000003bc <crt_section_copy>:
 3bc:	02b57c63          	bgeu	a0,a1,3f4 <crt_section_copy+0x38>
 3c0:	00b562b3          	or	t0,a0,a1
 3c4:	00c2e2b3          	or	t0,t0,a2
 3c8:	0032f293          	andi	t0,t0,3
 3cc:	02029863          	bnez	t0,3fc <crt_section_copy+0x40>
 3d0:	40c502b3          	sub	t0,a0,a2
 3d4:	40a58333          	sub	t1,a1,a0
 3d8:	0262e263          	bltu	t0,t1,3fc <crt_section_copy+0x40>
 3dc:	00062283          	lw	t0,0(a2)
 3e0:	00460613          	addi	a2,a2,4
 3e4:	00552023          	sw	t0,0(a0)
 3e8:	00450513          	addi	a0,a0,4
 3ec:	feb568e3          	bltu	a0,a1,3dc <crt_section_copy+0x20>
 3f0:	00008067          	ret
 3f4:	00b51463          	bne	a0,a1,3fc <crt_section_copy+0x40>
 3f8:	00008067          	ret
 3fc:	00100073          	ebreak
