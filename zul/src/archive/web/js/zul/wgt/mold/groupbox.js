/* groupbox.js

	Purpose:
		
	Description:
		
	History:
		Sun Nov 16 12:47:07     2008, Created by tomyeh

Copyright (C) 2008 Potix Corporation. All Rights Reserved.

This program is distributed under LGPL Version 3.0 in the hope that
it will be useful, but WITHOUT ANY WARRANTY.
*/
function (out, skipper) {	
	var	zcls = this.getZclass(),
		uuid = this.uuid,
		cap = this.caption,
		title = this.getTitle();
	title = title && !cap ? title : cap ? null: '&nbsp';

	out.push('<div', this.domAttrs_(), '>');

	if (title || cap) {
		out.push('<div class="', zcls, '-tl"><div class="', zcls,
			'-tr"></div></div><div class="', zcls, '-hl"><div class="',
			zcls, '-hr"><div class="', zcls, '-hm',(this._closable? '': ' ' + zcls + '-hm-readonly'),'"><div class="',
			zcls, '-header">');
		if (cap)
			cap.redraw(out);
		else
			out.push('<div id="', uuid,'-title" class="', zcls,'-title"><span>', title, '</span></div>');
		out.push('</div></div></div></div>');
	}
	
	this._redrawCave(out, skipper);

	//shadow
	out.push('<div id="', uuid, '-sdw" class="', zcls, '-bl"><div class="',
		zcls, '-br"><div class="', zcls, '-bm"></div></div></div></div>');
}