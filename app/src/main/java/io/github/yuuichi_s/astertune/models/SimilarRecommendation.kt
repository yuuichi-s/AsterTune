package io.github.yuuichi_s.astertune.models

import io.github.yuuichi_s.astertune.db.entities.LocalItem
import com.zionhuang.innertube.models.YTItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
