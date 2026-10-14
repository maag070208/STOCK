import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { newId } from "@shared/lib/newId";

export interface Toast {
	id: string;
	message: string;
	type: "success" | "error" | "info";
}

const slice = createSlice({
	name: "toast",
	initialState: [] as Toast[],
	reducers: {
		toastShown: {
			reducer(state, action: PayloadAction<Toast>) {
				state.push(action.payload);
			},
			prepare(message: string, type: Toast["type"] = "success") {
				return { payload: { id: newId(), message, type } };
			},
		},
		toastClosed(state, action: PayloadAction<string>) {
			return state.filter((toast) => toast.id !== action.payload);
		},
	},
});

export const { toastShown, toastClosed } = slice.actions;
export const toastReducer = slice.reducer;
