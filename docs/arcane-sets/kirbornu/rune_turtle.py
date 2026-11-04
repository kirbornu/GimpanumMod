"""Черепаха рунной магии — тот же расчёт, что в моде (magic/core/Turtle), на Python."""
import json, math, itertools, os
M=json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'magic.json')))
RUNES=["an","be","vol","gi","dar","er","zhin","zar","im","kol","lun","mar"]
V=[tuple(M["runes"][r]) for r in RUNES]
ANG=math.radians(M["turnAngle"]); STEP=M["stepMultiplier"]
def mul(a,b):
    w1,x1,y1,z1=a;w2,x2,y2,z2=b
    return (w1*w2-x1*x2-y1*y2-z1*z2,w1*x2+x1*w2+y1*z2-z1*y2,w1*y2-x1*z2+y1*w2+z1*x2,w1*z2+x1*y2-y1*x2+z1*w2)
def rot(q,v):
    r=mul(mul(q,(0,)+tuple(v)),(q[0],-q[1],-q[2],-q[3])); return r[1:]
def aa(u,t):
    n=math.sqrt(sum(c*c for c in u))
    if n==0: return (1,0,0,0)
    s=math.sin(t/2); return (math.cos(t/2),)+tuple(c/n*s for c in u)
def walk(word):
    p=[0,0,0]; q=(1,0,0,0)
    for i in word:
        v=[c*STEP for c in V[i]]; d=rot(q,v); p=[p[k]+d[k] for k in range(3)]; q=mul(q,aa(V[i],ANG))
    return tuple(p)
def words(maxlen):
    for n in range(1,maxlen+1):
        for w in itertools.product(range(12),repeat=n): yield w
