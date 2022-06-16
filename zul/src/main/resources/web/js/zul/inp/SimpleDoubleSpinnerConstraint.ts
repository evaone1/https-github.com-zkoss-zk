/**
 * A simple double spinner constraint.
 * @disable(zkgwt)
 */
export class SimpleDoubleSpinnerConstraint extends zul.inp.SimpleConstraint {
	public _min?: number;
	public _max?: number;

	/** Returns the minimum value.
	 * @return double
	 */
	public getMin(): number | undefined {
		return this._min;
	}

	/** Set the minimum value.
	 * @param double min
	 */
	public setMin(min: number): this {
		this._min = min;
		return this;
	}

	/** Returns the maximum value.
	 * @return double
	 */
	public getMax(): number | undefined {
		return this._max;
	}

	/** Set the maximum value.
	 * @param double max
	 */
	public setMax(max: number): this {
		this._max = max;
		return this;
	}

	protected override parseConstraint_(cst: string): void {
		var cstList = cst.replace(/ +/g, ' ').split(/[, ]/),
			len = cstList.length,
		isSpinner;
		for (var i = 0; i < len + 1; i++) {
			if (cstList[i] == 'min') {
				this._min = (cstList[++i] as unknown as number) * 1;
				isSpinner = true;
			} else if (cstList[i] == 'max') {
				this._max = (cstList[++i] as unknown as number) * 1;
				isSpinner = true;
			}
		}
		if (isSpinner) return;
		else
			return super.parseConstraint_(cst);
	}

	public override validate(wgt: zk.Widget, val: unknown): string | zul.inp.SimpleConstraintErrorMessages | undefined {
		var result = super.validate(wgt, val);
		switch (typeof val) {
			case 'number':
				if ((this._max && val > this._max) || (this._min && val < this._min)) {
					var msg: string | undefined = msgzul.OUT_OF_RANGE + ': ';
					msg += '(' + this._min != null ? this._max != null ?
							this._min + ' ~ ' + this._max : '>= ' + this._min : '<= ' + this._max + ')';
				}
		}
		if (msg)
			return msg;
		else
			return result;
	}
}
zul.inp.SimpleDoubleSpinnerConstraint = zk.regClass(SimpleDoubleSpinnerConstraint);