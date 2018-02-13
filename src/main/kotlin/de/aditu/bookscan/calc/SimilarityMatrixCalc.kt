package de.aditu.bookscan.calc

import de.aditu.bookscan.model.BookMatchMerge
import de.aditu.bookscan.model.Matrix
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.stream.Collectors

/**
 * calculates a similarity matrix
 */
@Service
class SimilarityMatrixCalc(@Autowired private val similarityCalc: SimilarityCalc) {

    /**
     * calculates the similarity matrix
     * matrix has a node list with node names (blog urls) and the matrix as two dimensional array (as list collection)
     */
    fun calc(matches: List<BookMatchMerge>): Matrix {
        val matchesByBlog = matches.groupBy({ it.mblogUrl }, { it })
        val similarity = similarityCalc.calc(matchesByBlog)

        val nodeSum = mutableMapOf<String, Long>()
        for (entry in similarity) {
            nodeSum.put(entry.key.first, (nodeSum[entry.key.first] ?: 0) + entry.value)
            nodeSum.put(entry.key.second, (nodeSum[entry.key.second] ?: 0) + entry.value)
        }

        val nodes = matchesByBlog.keys
                .stream()
                .sorted { o1, o2 -> (nodeSum[o2] ?: 0).compareTo(nodeSum[o1] ?: 0) }
                .collect(Collectors.toList())

        val m = mutableListOf<List<Long>>()
        for (source in nodes) {
            val line = mutableListOf<Long>()
            for (target in nodes) {
                when {
                    source == target -> line.add(0)
                    similarity.containsKey(Pair(source,target)) -> line.add((similarity[Pair(source, target)] ?: 0))
                    else -> line.add((similarity[Pair(target, source)] ?: 0))
                }
            }
            m.add(line)
        }

        return Matrix(nodes, m)
    }
}