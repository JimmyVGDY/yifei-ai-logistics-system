<template>
  <div class="table-pagination">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @current-change="$emit('page-change', $event)"
      @size-change="$emit('page-size-change', $event)"
    />
  </div>
</template>

<script setup>
defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 }
})

defineEmits(['page-change', 'page-size-change'])
</script>
