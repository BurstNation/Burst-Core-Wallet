package nxt.http;

import nxt.Asset;
import nxt.Nxt;
import nxt.Trade;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static nxt.http.JSONResponses.INCORRECT_ASSET;
import static nxt.http.JSONResponses.MISSING_ASSET;
import static nxt.http.JSONResponses.UNKNOWN_ASSET;

public final class GetTrades extends APIServlet.APIRequestHandler {

    static final GetTrades instance = new GetTrades();

    private GetTrades() {}

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) {

        String asset = req.getParameter("asset");
        if (asset == null) {
            return MISSING_ASSET;
        }

        Long assetId;
        try {
            assetId = Convert.parseUnsignedLong(asset);
            if (Asset.getAsset(assetId) == null) {
                return UNKNOWN_ASSET;
            }
        } catch (RuntimeException e) {
            return INCORRECT_ASSET;
        }

        int firstIndex, lastIndex;
        try {
            firstIndex = Integer.parseInt(req.getParameter("firstIndex"));
        } catch (NumberFormatException e) {
            firstIndex = 0;
        }
        try {
            lastIndex = Integer.parseInt(req.getParameter("lastIndex"));
        } catch (NumberFormatException e) {
            lastIndex = Integer.MAX_VALUE;
        }

        JSONObject response = new JSONObject();

        JSONArray tradesData = new JSONArray();
        try {
            List<Trade> trades = Trade.getTrades(assetId);
            for (int i = firstIndex; i <= lastIndex; i++) {
                Trade trade = trades.get(i);

                JSONObject tradeData = new JSONObject();
                tradeData.put("timestamp", Nxt.getBlockchain().getBlock(trade.getBlockId()).getTimestamp());
                tradeData.put("askOrderId", Convert.toUnsignedLong(trade.getAskOrderId()));
                tradeData.put("bidOrderId", Convert.toUnsignedLong(trade.getBidOrderId()));
                tradeData.put("quantity", trade.getQuantity());
                tradeData.put("price", trade.getPrice());

                tradesData.add(tradeData);
            }
        } catch (RuntimeException e) {}
        response.put("trades", tradesData);

        return response;
    }

}
