
bazel-out/k8-fastbuild-ST-dd8dc713f32d/bin/tests/cocotb/rvv/ml_ops/rvv_matmul_16x32_32x16.elf:     file format elf32-littleriscv


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
  88:	05018513          	addi	a0,gp,80 # 10850 <mcontext0_write_value>
  8c:	00011597          	auipc	a1,0x11
  90:	80c58593          	addi	a1,a1,-2036 # 10898 <__bss_end>
  94:	6bc000ef          	jal	750 <crt_section_clear>
  98:	7c000413          	li	s0,1984
  9c:	7c000493          	li	s1,1984
  a0:	00947a63          	bgeu	s0,s1,b4 <init_array_loop_end>

000000a4 <init_array_loop>:
  a4:	00042283          	lw	t0,0(s0)
  a8:	000280e7          	jalr	t0
  ac:	00440413          	addi	s0,s0,4
  b0:	fe946ae3          	bltu	s0,s1,a4 <init_array_loop>

000000b4 <init_array_loop_end>:
  b4:	00000297          	auipc	t0,0x0
  b8:	5bc28293          	addi	t0,t0,1468 # 670 <coralnpu_exception_handler>
  bc:	30529073          	csrw	mtvec,t0
  c0:	000062b7          	lui	t0,0x6
  c4:	60028293          	addi	t0,t0,1536 # 6600 <__fini_array_end+0x5e40>
  c8:	3002a073          	csrs	mstatus,t0
  cc:	00010297          	auipc	t0,0x10
  d0:	77428293          	addi	t0,t0,1908 # 10840 <_ret>
  d4:	0badd537          	lui	a0,0xbadd
  d8:	00d50513          	addi	a0,a0,13 # badd00d <__stack_end__+0xbac500d>
  dc:	00a2a023          	sw	a0,0(t0)
  e0:	00000513          	li	a0,0
  e4:	00000593          	li	a1,0
  e8:	00000097          	auipc	ra,0x0
  ec:	4bc08093          	addi	ra,ra,1212 # 5a4 <main>
  f0:	000080e7          	jalr	ra
  f4:	00050913          	mv	s2,a0
  f8:	5d4000ef          	jal	6cc <__cxa_finalize>
  fc:	7c000413          	li	s0,1984
 100:	7c000493          	li	s1,1984
 104:	00940a63          	beq	s0,s1,118 <fini_array_loop_end>

00000108 <fini_array_loop>:
 108:	ffc48493          	addi	s1,s1,-4
 10c:	0004a283          	lw	t0,0(s1)
 110:	000280e7          	jalr	t0
 114:	fe941ae3          	bne	s0,s1,108 <fini_array_loop>

00000118 <fini_array_loop_end>:
 118:	00090513          	mv	a0,s2
 11c:	00010297          	auipc	t0,0x10
 120:	72428293          	addi	t0,t0,1828 # 10840 <_ret>
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

00000144 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl>:
 144:	fe010113          	addi	sp,sp,-32
 148:	00812e23          	sw	s0,28(sp)
 14c:	01612223          	sw	s6,4(sp)
 150:	00060413          	mv	s0,a2
 154:	00068b13          	mv	s6,a3
 158:	00070613          	mv	a2,a4
 15c:	00300793          	li	a5,3
 160:	20b7fc63          	bgeu	a5,a1,378 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x234>
 164:	00912c23          	sw	s1,24(sp)
 168:	01212a23          	sw	s2,20(sp)
 16c:	01312823          	sw	s3,16(sp)
 170:	01412623          	sw	s4,12(sp)
 174:	01512423          	sw	s5,8(sp)
 178:	00070493          	mv	s1,a4
 17c:	00259e93          	slli	t4,a1,0x2
 180:	ffe50813          	addi	a6,a0,-2
 184:	ffe87793          	andi	a5,a6,-2
 188:	00278793          	addi	a5,a5,2
 18c:	00253713          	sltiu	a4,a0,2
 190:	fff70713          	addi	a4,a4,-1
 194:	00e7f7b3          	and	a5,a5,a4
 198:	00078a13          	mv	s4,a5
 19c:	00579a93          	slli	s5,a5,0x5
 1a0:	01540ab3          	add	s5,s0,s5
 1a4:	02f58933          	mul	s2,a1,a5
 1a8:	00400713          	li	a4,4
 1ac:	02000793          	li	a5,32
 1b0:	00100993          	li	s3,1
 1b4:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 1b8:	420060d7          	vmv.s.x	v1,zero
 1bc:	00185f13          	srli	t5,a6,0x1
 1c0:	006f1f13          	slli	t5,t5,0x6
 1c4:	04040813          	addi	a6,s0,64
 1c8:	010f0f33          	add	t5,t5,a6
 1cc:	00ee83b3          	add	t2,t4,a4
 1d0:	008e8293          	addi	t0,t4,8
 1d4:	00ce8f93          	addi	t6,t4,12
 1d8:	ffc90913          	addi	s2,s2,-4
 1dc:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 1e0:	1240006f          	j	304 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x1c0>
 1e4:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 1e8:	01d80333          	add	t1,a6,t4
 1ec:	02088207          	vle8.v	v4,(a7)
 1f0:	02088e13          	addi	t3,a7,32
 1f4:	020e0107          	vle8.v	v2,(t3)
 1f8:	ee4d2e57          	vwmul.vv	v28,v4,v26
 1fc:	ee4c2857          	vwmul.vv	v16,v4,v24
 200:	ee4b2657          	vwmul.vv	v12,v4,v22
 204:	ee4a2457          	vwmul.vv	v8,v4,v20
 208:	0ca07057          	vsetvli	zero,zero,e16,m4,ta,ma
 20c:	c7c08e57          	vwredsum.vs	v28,v28,v1
 210:	c7008857          	vwredsum.vs	v16,v16,v1
 214:	c6c08657          	vwredsum.vs	v12,v12,v1
 218:	c6808457          	vwredsum.vs	v8,v8,v1
 21c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 220:	02086e27          	vse32.v	v28,(a6)
 224:	00480e13          	addi	t3,a6,4
 228:	020e6827          	vse32.v	v16,(t3)
 22c:	00880e13          	addi	t3,a6,8
 230:	020e6627          	vse32.v	v12,(t3)
 234:	00c80e13          	addi	t3,a6,12
 238:	020e6427          	vse32.v	v8,(t3)
 23c:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 240:	ee2d2857          	vwmul.vv	v16,v2,v26
 244:	ee2c2657          	vwmul.vv	v12,v2,v24
 248:	ee2b2457          	vwmul.vv	v8,v2,v22
 24c:	ee2a2257          	vwmul.vv	v4,v2,v20
 250:	0ca07057          	vsetvli	zero,zero,e16,m4,ta,ma
 254:	c7008857          	vwredsum.vs	v16,v16,v1
 258:	c6c08657          	vwredsum.vs	v12,v12,v1
 25c:	c6808457          	vwredsum.vs	v8,v8,v1
 260:	c6408257          	vwredsum.vs	v4,v4,v1
 264:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 268:	02036827          	vse32.v	v16,(t1)
 26c:	01038e33          	add	t3,t2,a6
 270:	020e6627          	vse32.v	v12,(t3)
 274:	01028e33          	add	t3,t0,a6
 278:	020e6427          	vse32.v	v8,(t3)
 27c:	010f8833          	add	a6,t6,a6
 280:	02086227          	vse32.v	v4,(a6)
 284:	04088893          	addi	a7,a7,64
 288:	006e8833          	add	a6,t4,t1
 28c:	f5e89ce3          	bne	a7,t5,1e4 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0xa0>
 290:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 294:	04aa7e63          	bgeu	s4,a0,2f0 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x1ac>
 298:	00e90833          	add	a6,s2,a4
 29c:	00281813          	slli	a6,a6,0x2
 2a0:	01060833          	add	a6,a2,a6
 2a4:	020a8107          	vle8.v	v2,(s5)
 2a8:	ee2d2857          	vwmul.vv	v16,v2,v26
 2ac:	ee2c2657          	vwmul.vv	v12,v2,v24
 2b0:	ee2b2457          	vwmul.vv	v8,v2,v22
 2b4:	ee2a2257          	vwmul.vv	v4,v2,v20
 2b8:	0ca07057          	vsetvli	zero,zero,e16,m4,ta,ma
 2bc:	c7008857          	vwredsum.vs	v16,v16,v1
 2c0:	c6c08657          	vwredsum.vs	v12,v12,v1
 2c4:	c6808457          	vwredsum.vs	v8,v8,v1
 2c8:	c6408257          	vwredsum.vs	v4,v4,v1
 2cc:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 2d0:	02086827          	vse32.v	v16,(a6)
 2d4:	00480893          	addi	a7,a6,4
 2d8:	0208e627          	vse32.v	v12,(a7)
 2dc:	00880893          	addi	a7,a6,8
 2e0:	0208e427          	vse32.v	v8,(a7)
 2e4:	00c80813          	addi	a6,a6,12
 2e8:	02086227          	vse32.v	v4,(a6)
 2ec:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 2f0:	00470813          	addi	a6,a4,4
 2f4:	08068693          	addi	a3,a3,128
 2f8:	01048493          	addi	s1,s1,16
 2fc:	0305ea63          	bltu	a1,a6,330 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x1ec>
 300:	00080713          	mv	a4,a6
 304:	02068d07          	vle8.v	v26,(a3)
 308:	02068813          	addi	a6,a3,32
 30c:	02080c07          	vle8.v	v24,(a6)
 310:	04068813          	addi	a6,a3,64
 314:	02080b07          	vle8.v	v22,(a6)
 318:	06068813          	addi	a6,a3,96
 31c:	02080a07          	vle8.v	v20,(a6)
 320:	f6a9fae3          	bgeu	s3,a0,294 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x150>
 324:	00040893          	mv	a7,s0
 328:	00048813          	mv	a6,s1
 32c:	ebdff06f          	j	1e8 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0xa4>
 330:	01812483          	lw	s1,24(sp)
 334:	01412903          	lw	s2,20(sp)
 338:	01012983          	lw	s3,16(sp)
 33c:	00c12a03          	lw	s4,12(sp)
 340:	00812a83          	lw	s5,8(sp)
 344:	00270f13          	addi	t5,a4,2
 348:	0be5e263          	bltu	a1,t5,3ec <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x2a8>
 34c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 350:	00271e93          	slli	t4,a4,0x2
 354:	01d60eb3          	add	t4,a2,t4
 358:	00571713          	slli	a4,a4,0x5
 35c:	00eb0e33          	add	t3,s6,a4
 360:	00259693          	slli	a3,a1,0x2
 364:	00551713          	slli	a4,a0,0x5
 368:	00870733          	add	a4,a4,s0
 36c:	02000793          	li	a5,32
 370:	420060d7          	vmv.s.x	v1,zero
 374:	0580006f          	j	3cc <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x288>
 378:	00000713          	li	a4,0
 37c:	fc9ff06f          	j	344 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x200>
 380:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 384:	02088107          	vle8.v	v2,(a7)
 388:	ee272457          	vwmul.vv	v8,v2,v14
 38c:	ee262257          	vwmul.vv	v4,v2,v12
 390:	0ca07057          	vsetvli	zero,zero,e16,m4,ta,ma
 394:	c6808457          	vwredsum.vs	v8,v8,v1
 398:	c6408257          	vwredsum.vs	v4,v4,v1
 39c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 3a0:	02086427          	vse32.v	v8,(a6)
 3a4:	00480313          	addi	t1,a6,4
 3a8:	02036227          	vse32.v	v4,(t1)
 3ac:	02088893          	addi	a7,a7,32
 3b0:	00d80833          	add	a6,a6,a3
 3b4:	fce896e3          	bne	a7,a4,380 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x23c>
 3b8:	002f0813          	addi	a6,t5,2
 3bc:	008e8e93          	addi	t4,t4,8
 3c0:	040e0e13          	addi	t3,t3,64
 3c4:	0305e663          	bltu	a1,a6,3f0 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x2ac>
 3c8:	00080f13          	mv	t5,a6
 3cc:	fe0506e3          	beqz	a0,3b8 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x274>
 3d0:	0c17f057          	vsetvli	zero,a5,e8,m2,ta,ma
 3d4:	020e0707          	vle8.v	v14,(t3)
 3d8:	020e0813          	addi	a6,t3,32
 3dc:	02080607          	vle8.v	v12,(a6)
 3e0:	000e8813          	mv	a6,t4
 3e4:	00040893          	mv	a7,s0
 3e8:	f9dff06f          	j	384 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x240>
 3ec:	00070f13          	mv	t5,a4
 3f0:	06bf7263          	bgeu	t5,a1,454 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x310>
 3f4:	06050063          	beqz	a0,454 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x310>
 3f8:	005f1793          	slli	a5,t5,0x5
 3fc:	00fb07b3          	add	a5,s6,a5
 400:	02000713          	li	a4,32
 404:	0c177057          	vsetvli	zero,a4,e8,m2,ta,ma
 408:	02078407          	vle8.v	v8,(a5)
 40c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 410:	420060d7          	vmv.s.x	v1,zero
 414:	00040793          	mv	a5,s0
 418:	00259593          	slli	a1,a1,0x2
 41c:	002f1f13          	slli	t5,t5,0x2
 420:	01e60633          	add	a2,a2,t5
 424:	00551513          	slli	a0,a0,0x5
 428:	00a40433          	add	s0,s0,a0
 42c:	0c177057          	vsetvli	zero,a4,e8,m2,ta,ma
 430:	02078107          	vle8.v	v2,(a5)
 434:	ee242257          	vwmul.vv	v4,v2,v8
 438:	0ca07057          	vsetvli	zero,zero,e16,m4,ta,ma
 43c:	c6408257          	vwredsum.vs	v4,v4,v1
 440:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 444:	02066227          	vse32.v	v4,(a2)
 448:	02078793          	addi	a5,a5,32
 44c:	00b60633          	add	a2,a2,a1
 450:	fcf41ee3          	bne	s0,a5,42c <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl+0x2e8>
 454:	01c12403          	lw	s0,28(sp)
 458:	00412b03          	lw	s6,4(sp)
 45c:	02010113          	addi	sp,sp,32
 460:	00008067          	ret

00000464 <_Z6MatMuljjjPKaS0_Pl>:
 464:	ff010113          	addi	sp,sp,-16
 468:	00112623          	sw	ra,12(sp)
 46c:	00058313          	mv	t1,a1
 470:	00060593          	mv	a1,a2
 474:	00068613          	mv	a2,a3
 478:	00070693          	mv	a3,a4
 47c:	00078713          	mv	a4,a5
 480:	fe030813          	addi	a6,t1,-32
 484:	00183813          	seqz	a6,a6
 488:	ff058793          	addi	a5,a1,-16
 48c:	0017b793          	seqz	a5,a5
 490:	00f877b3          	and	a5,a6,a5
 494:	00078663          	beqz	a5,4a0 <_Z6MatMuljjjPKaS0_Pl+0x3c>
 498:	ff050793          	addi	a5,a0,-16
 49c:	04078063          	beqz	a5,4dc <_Z6MatMuljjjPKaS0_Pl+0x78>
 4a0:	c2202ff3          	csrr	t6,vlenb
 4a4:	010fb793          	sltiu	a5,t6,16
 4a8:	00079463          	bnez	a5,4b0 <_Z6MatMuljjjPKaS0_Pl+0x4c>
 4ac:	04081663          	bnez	a6,4f8 <_Z6MatMuljjjPKaS0_Pl+0x94>
 4b0:	0e050463          	beqz	a0,598 <_Z6MatMuljjjPKaS0_Pl+0x134>
 4b4:	00812423          	sw	s0,8(sp)
 4b8:	00259413          	slli	s0,a1,0x2
 4bc:	00870f33          	add	t5,a4,s0
 4c0:	40b003b3          	neg	t2,a1
 4c4:	00239393          	slli	t2,t2,0x2
 4c8:	00000293          	li	t0,0
 4cc:	00000713          	li	a4,0
 4d0:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 4d4:	42006157          	vmv.s.x	v2,zero
 4d8:	0ac0006f          	j	584 <_Z6MatMuljjjPKaS0_Pl+0x120>
 4dc:	c22028f3          	csrr	a7,vlenb
 4e0:	00f00793          	li	a5,15
 4e4:	0117ee63          	bltu	a5,a7,500 <_Z6MatMuljjjPKaS0_Pl+0x9c>
 4e8:	c2202ff3          	csrr	t6,vlenb
 4ec:	010fb793          	sltiu	a5,t6,16
 4f0:	fc0792e3          	bnez	a5,4b4 <_Z6MatMuljjjPKaS0_Pl+0x50>
 4f4:	fc0800e3          	beqz	a6,4b4 <_Z6MatMuljjjPKaS0_Pl+0x50>
 4f8:	c4dff0ef          	jal	144 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl>
 4fc:	09c0006f          	j	598 <_Z6MatMuljjjPKaS0_Pl+0x134>
 500:	01000593          	li	a1,16
 504:	00058513          	mv	a0,a1
 508:	c3dff0ef          	jal	144 <_ZL24MatMul_inner32_rhs_reusejjPKaS0_Pl>
 50c:	08c0006f          	j	598 <_Z6MatMuljjjPKaS0_Pl+0x134>
 510:	9e2030d7          	vmv1r.v	v1,v2
 514:	0207e0a7          	vse32.v	v1,(a5)
 518:	00478793          	addi	a5,a5,4
 51c:	006e0e33          	add	t3,t3,t1
 520:	05e78a63          	beq	a5,t5,574 <_Z6MatMuljjjPKaS0_Pl+0x110>
 524:	fe0306e3          	beqz	t1,510 <_Z6MatMuljjjPKaS0_Pl+0xac>
 528:	9e2030d7          	vmv1r.v	v1,v2
 52c:	000f8893          	mv	a7,t6
 530:	00000813          	li	a6,0
 534:	41030eb3          	sub	t4,t1,a6
 538:	0bd8d8b3          	minu	a7,a7,t4
 53c:	00580eb3          	add	t4,a6,t0
 540:	01d60eb3          	add	t4,a2,t4
 544:	0c08f057          	vsetvli	zero,a7,e8,m1,ta,ma
 548:	020e8307          	vle8.v	v6,(t4)
 54c:	010e0eb3          	add	t4,t3,a6
 550:	01d68eb3          	add	t4,a3,t4
 554:	020e8187          	vle8.v	v3,(t4)
 558:	ee61a257          	vwmul.vv	v4,v6,v3
 55c:	0c9ff057          	vsetvli	zero,t6,e16,m2,ta,ma
 560:	c64080d7          	vwredsum.vs	v1,v4,v1
 564:	01180833          	add	a6,a6,a7
 568:	fc6866e3          	bltu	a6,t1,534 <_Z6MatMuljjjPKaS0_Pl+0xd0>
 56c:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
 570:	fa5ff06f          	j	514 <_Z6MatMuljjjPKaS0_Pl+0xb0>
 574:	00170713          	addi	a4,a4,1
 578:	008f0f33          	add	t5,t5,s0
 57c:	006282b3          	add	t0,t0,t1
 580:	00e50a63          	beq	a0,a4,594 <_Z6MatMuljjjPKaS0_Pl+0x130>
 584:	fe0588e3          	beqz	a1,574 <_Z6MatMuljjjPKaS0_Pl+0x110>
 588:	007f07b3          	add	a5,t5,t2
 58c:	00000e13          	li	t3,0
 590:	f95ff06f          	j	524 <_Z6MatMuljjjPKaS0_Pl+0xc0>
 594:	00812403          	lw	s0,8(sp)
 598:	00c12083          	lw	ra,12(sp)
 59c:	01010113          	addi	sp,sp,16
 5a0:	00008067          	ret

000005a4 <main>:
 5a4:	fe010113          	addi	sp,sp,-32
 5a8:	00112e23          	sw	ra,28(sp)
 5ac:	00812c23          	sw	s0,24(sp)
 5b0:	00912a23          	sw	s1,20(sp)
 5b4:	01212823          	sw	s2,16(sp)
 5b8:	01312623          	sw	s3,12(sp)
 5bc:	00010417          	auipc	s0,0x10
 5c0:	a4440413          	addi	s0,s0,-1468 # 10000 <bench_result>
 5c4:	00100793          	li	a5,1
 5c8:	00f42023          	sw	a5,0(s0)
 5cc:	00042223          	sw	zero,4(s0)
 5d0:	00042423          	sw	zero,8(s0)
 5d4:	00042623          	sw	zero,12(s0)
 5d8:	05018913          	addi	s2,gp,80 # 10850 <mcontext0_write_value>
 5dc:	00f92023          	sw	a5,0(s2)
 5e0:	7c079073          	csrw	0x7c0,a5
 5e4:	b80027f3          	csrr	a5,mcycleh
 5e8:	b00024f3          	csrr	s1,mcycle
 5ec:	b80029f3          	csrr	s3,mcycleh
 5f0:	ff379ae3          	bne	a5,s3,5e4 <main+0x40>
 5f4:	84018793          	addi	a5,gp,-1984 # 10040 <result_output>
 5f8:	c4018713          	addi	a4,gp,-960 # 10440 <rhs_input>
 5fc:	e4018693          	addi	a3,gp,-448 # 10640 <lhs_input>
 600:	01000613          	li	a2,16
 604:	02000593          	li	a1,32
 608:	00060513          	mv	a0,a2
 60c:	e59ff0ef          	jal	464 <_Z6MatMuljjjPKaS0_Pl>
 610:	b8002773          	csrr	a4,mcycleh
 614:	b00026f3          	csrr	a3,mcycle
 618:	b80027f3          	csrr	a5,mcycleh
 61c:	fef71ae3          	bne	a4,a5,610 <main+0x6c>
 620:	00092023          	sw	zero,0(s2)
 624:	00000713          	li	a4,0
 628:	7c071073          	csrw	0x7c0,a4
 62c:	40968733          	sub	a4,a3,s1
 630:	00e6b6b3          	sltu	a3,a3,a4
 634:	413787b3          	sub	a5,a5,s3
 638:	40d787b3          	sub	a5,a5,a3
 63c:	00e42223          	sw	a4,4(s0)
 640:	00f42423          	sw	a5,8(s0)
 644:	4d4c57b7          	lui	a5,0x4d4c5
 648:	f5078793          	addi	a5,a5,-176 # 4d4c4f50 <__extbss_end__+0x2d4c4f50>
 64c:	00f42623          	sw	a5,12(s0)
 650:	00000513          	li	a0,0
 654:	01c12083          	lw	ra,28(sp)
 658:	01812403          	lw	s0,24(sp)
 65c:	01412483          	lw	s1,20(sp)
 660:	01012903          	lw	s2,16(sp)
 664:	00c12983          	lw	s3,12(sp)
 668:	02010113          	addi	sp,sp,32
 66c:	00008067          	ret

00000670 <coralnpu_exception_handler>:
 670:	00100073          	ebreak
 674:	0000006f          	j	674 <coralnpu_exception_handler+0x4>

00000678 <__cxa_guard_acquire>:
 678:	00054503          	lbu	a0,0(a0)
 67c:	00153513          	seqz	a0,a0
 680:	00008067          	ret

00000684 <__cxa_guard_release>:
 684:	00100793          	li	a5,1
 688:	00f50023          	sb	a5,0(a0)
 68c:	00008067          	ret

00000690 <__cxa_guard_abort>:
 690:	00008067          	ret

00000694 <__cxa_atexit>:
 694:	0541a783          	lw	a5,84(gp) # 10854 <_ZN10__cxxabiv1L12atexit_countE>
 698:	00700713          	li	a4,7
 69c:	02f74463          	blt	a4,a5,6c4 <__cxa_atexit+0x30>
 6a0:	00379693          	slli	a3,a5,0x3
 6a4:	05818713          	addi	a4,gp,88 # 10858 <_ZN10__cxxabiv1L14atexit_entriesE>
 6a8:	00d70733          	add	a4,a4,a3
 6ac:	00a72023          	sw	a0,0(a4)
 6b0:	00b72223          	sw	a1,4(a4)
 6b4:	00178793          	addi	a5,a5,1
 6b8:	04f1aa23          	sw	a5,84(gp) # 10854 <_ZN10__cxxabiv1L12atexit_countE>
 6bc:	00000513          	li	a0,0
 6c0:	00008067          	ret
 6c4:	fff00513          	li	a0,-1
 6c8:	00008067          	ret

000006cc <__cxa_finalize>:
 6cc:	ff010113          	addi	sp,sp,-16
 6d0:	00112623          	sw	ra,12(sp)
 6d4:	00912223          	sw	s1,4(sp)
 6d8:	0541a783          	lw	a5,84(gp) # 10854 <_ZN10__cxxabiv1L12atexit_countE>
 6dc:	fff78493          	addi	s1,a5,-1
 6e0:	0404c463          	bltz	s1,728 <__cxa_finalize+0x5c>
 6e4:	00812423          	sw	s0,8(sp)
 6e8:	01212023          	sw	s2,0(sp)
 6ec:	00379793          	slli	a5,a5,0x3
 6f0:	05818413          	addi	s0,gp,88 # 10858 <_ZN10__cxxabiv1L14atexit_entriesE>
 6f4:	00f40433          	add	s0,s0,a5
 6f8:	fff00913          	li	s2,-1
 6fc:	0100006f          	j	70c <__cxa_finalize+0x40>
 700:	fff48493          	addi	s1,s1,-1
 704:	ff840413          	addi	s0,s0,-8
 708:	01248c63          	beq	s1,s2,720 <__cxa_finalize+0x54>
 70c:	ff842783          	lw	a5,-8(s0)
 710:	fe0788e3          	beqz	a5,700 <__cxa_finalize+0x34>
 714:	ffc42503          	lw	a0,-4(s0)
 718:	000780e7          	jalr	a5
 71c:	fe5ff06f          	j	700 <__cxa_finalize+0x34>
 720:	00812403          	lw	s0,8(sp)
 724:	00012903          	lw	s2,0(sp)
 728:	0401aa23          	sw	zero,84(gp) # 10854 <_ZN10__cxxabiv1L12atexit_countE>
 72c:	00c12083          	lw	ra,12(sp)
 730:	00412483          	lw	s1,4(sp)
 734:	01010113          	addi	sp,sp,16
 738:	00008067          	ret

0000073c <__cxa_pure_virtual>:
 73c:	00100073          	ebreak
 740:	00008067          	ret
	...

Disassembly of section .crt:

00000750 <crt_section_clear>:
 750:	02b57063          	bgeu	a0,a1,770 <crt_section_clear+0x20>
 754:	00b562b3          	or	t0,a0,a1
 758:	0032f293          	andi	t0,t0,3
 75c:	00029e63          	bnez	t0,778 <crt_section_clear+0x28>
 760:	00052023          	sw	zero,0(a0)
 764:	00450513          	addi	a0,a0,4
 768:	feb56ce3          	bltu	a0,a1,760 <crt_section_clear+0x10>
 76c:	00008067          	ret
 770:	00b51463          	bne	a0,a1,778 <crt_section_clear+0x28>
 774:	00008067          	ret
 778:	00100073          	ebreak

0000077c <crt_section_copy>:
 77c:	02b57c63          	bgeu	a0,a1,7b4 <crt_section_copy+0x38>
 780:	00b562b3          	or	t0,a0,a1
 784:	00c2e2b3          	or	t0,t0,a2
 788:	0032f293          	andi	t0,t0,3
 78c:	02029863          	bnez	t0,7bc <crt_section_copy+0x40>
 790:	40c502b3          	sub	t0,a0,a2
 794:	40a58333          	sub	t1,a1,a0
 798:	0262e263          	bltu	t0,t1,7bc <crt_section_copy+0x40>
 79c:	00062283          	lw	t0,0(a2)
 7a0:	00460613          	addi	a2,a2,4
 7a4:	00552023          	sw	t0,0(a0)
 7a8:	00450513          	addi	a0,a0,4
 7ac:	feb568e3          	bltu	a0,a1,79c <crt_section_copy+0x20>
 7b0:	00008067          	ret
 7b4:	00b51463          	bne	a0,a1,7bc <crt_section_copy+0x40>
 7b8:	00008067          	ret
 7bc:	00100073          	ebreak
