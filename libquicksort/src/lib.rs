use std::os::raw::{c_double, c_int};
use std::slice;

#[unsafe(no_mangle)]
pub extern "C" fn sum(array_ptr: *mut f64, length: usize) -> f64 {
    let arr = unsafe { slice::from_raw_parts(array_ptr, length) };

    let mut sum = 0.0;
    for &value in arr.iter() {
        sum += value;
    }

    sum
}

/// Type alias for a comparator function pointer
type Comparator = extern "C" fn(a: c_double, b: c_double) -> c_int;

#[unsafe(no_mangle)]
pub extern "C" fn quicksort(array_ptr: *mut c_double, length: usize, cmp: Comparator) {
    let arr = unsafe { slice::from_raw_parts_mut(array_ptr, length) };

    quicksort_recursive(arr, &cmp);
}

fn quicksort_recursive(arr: &mut [c_double], cmp: &Comparator) {
    if arr.len() <= 1 {
        return;
    }

    let pivot_index = partition(arr, cmp);
    quicksort_recursive(&mut arr[0..pivot_index], cmp);
    quicksort_recursive(&mut arr[pivot_index + 1..], cmp);
}

fn partition(arr: &mut [c_double], cmp: &Comparator) -> usize {
    let len = arr.len();
    let pivot = arr[len - 1];
    let mut i = 0;
    for j in 0..len - 1 {
        if cmp(arr[j], pivot) < 0 {
            arr.swap(i, j);
            i += 1;
        }
    }
    arr.swap(i, len - 1);
    i
}
