#!/usr/bin/env python3
"""Research-only balanced complete-set capital placement candidate for retained Mini4X source."""
from __future__ import annotations
import sys
from pathlib import Path


def rep(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'balanced-capital patch expected one {label}, found {count}')
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit('usage: patch_balanced_capitals.py <sim-dir>')
    path = Path(sys.argv[1]) / 'MapGenerator.kt'
    s = path.read_text()

    insert = '''
    private fun edgeDistance(pos:Pos,n:Int)=minOf(pos.x,pos.y,n-1-pos.x,n-1-pos.y)

    private fun coefficientOfVariation(values:List<Int>):Double {
        if(values.isEmpty())return 0.0
        val avg=values.average();if(avg==0.0)return 0.0
        val variance=values.sumOf { v -> val d=v-avg;d*d }/values.size
        return sqrt(variance)/avg
    }

    private fun generationInfluenceAreas(capitals:List<Pos>,n:Int):List<Int> {
        val areas=IntArray(capitals.size)
        for(x in 0 until n)for(y in 0 until n){
            val p=Pos(x,y)
            val owner=capitals.indices.minByOrNull { i -> capitals[i].chebyshev(p)*1000+i }?:0
            areas[owner]++
        }
        return areas.toList()
    }

    private fun sampleCapitalSet(seed:Long,n:Int,count:Int,minSep:Int,attempt:Int):List<Pos>? {
        val attemptSeed=seed xor ((attempt+1).toLong()*-7046029254386353131L)
        val candidates=(1 until n-1).flatMap{x->(1 until n-1).map{y->Pos(x,y)}}
            .sortedBy { hash(attemptSeed,it.x,it.y,711) }
        val selected=mutableListOf<Pos>()
        for(candidate in candidates){
            if(selected.all{it.chebyshev(candidate)>=minSep})selected+=candidate
            if(selected.size==count)return selected.sortedBy{hash(seed,it.x,it.y,812)}
        }
        return null
    }

    private fun capitalSetScore(capitals:List<Pos>,n:Int):Double {
        val influence=generationInfluenceAreas(capitals,n)
        val edges=capitals.map{edgeDistance(it,n)}
        return coefficientOfVariation(influence)+
            .10*coefficientOfVariation(edges)+
            .02*edges.map{max(0,2-it)}.average()
    }

    private fun chooseBalancedCapitals(seed:Long,n:Int,count:Int,minSep:Int,attempts:Int=16):List<Pos> {
        val options=(0 until attempts).mapNotNull{attempt->sampleCapitalSet(seed,n,count,minSep,attempt)}
        if(options.isEmpty())return chooseSeparated(seed,n,count,minSep=minSep).sortedBy{hash(seed,it.x,it.y,812)}
        return options.minByOrNull{capitalSetScore(it,n)}!!
    }
'''
    s = rep(s, '\n    fun create(config: GameConfig): SimState {', insert + '\n    fun create(config: GameConfig): SimState {', 'create insertion')
    s = rep(s, '        val capitals=chooseSeparated(config.seed,n,pcount,minSep=sep)', '        val capitals=chooseBalancedCapitals(config.seed,n,pcount,minSep=sep)', 'capital selection')

    required = [
        'private fun chooseBalancedCapitals',
        'sampleCapitalSet(seed,n,count,minSep,attempt)',
        'capitalSetScore(it,n)',
        'val capitals=chooseBalancedCapitals(config.seed,n,pcount,minSep=sep)',
    ]
    missing = [x for x in required if x not in s]
    if missing:
        raise SystemExit('balanced-capital verification missing: ' + ', '.join(missing))
    path.write_text(s)
    print('balanced_capital_candidate=APPLIED attempts=16 scope=capital_selection_only')


if __name__ == '__main__':
    main()
